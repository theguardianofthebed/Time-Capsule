package com.example.timecapsule.data

import com.example.timecapsule.data.adapter.AuthAdapter
import com.example.timecapsule.data.adapter.DefaultLocalRetentionAdapter
import com.example.timecapsule.data.adapter.FirestoreAdapter
import com.example.timecapsule.data.adapter.LocalRetentionAdapter
import com.example.timecapsule.data.cleanup.AccountCleanupOrchestrator
import com.example.timecapsule.data.media.OriginalPhotoStore
import com.example.timecapsule.data.media.PhotoDraftJournal
import com.example.timecapsule.data.notification.NotificationAdapter
import com.example.timecapsule.data.notification.NotificationAttemptStore
import com.example.timecapsule.data.notification.NotificationPostResult
import com.example.timecapsule.data.notification.UnlockNotificationCoordinator
import com.example.timecapsule.data.publication.FrozenOperationJournal
import com.example.timecapsule.data.session.DefaultSessionTracker
import com.example.timecapsule.domain.model.Availability
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.domain.model.OriginalPhotoFormat
import com.example.timecapsule.domain.model.ProtectedCapsuleContent
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.model.RecipientUnlockRequirements
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import android.app.Application
import com.example.timecapsule.data.media.AndroidOriginalImageProbe
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Test-driven regression suite verifying session generation write gates,
 * fail-closed cleanup gates, PNG photo format preservation, and failed photo download handling.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StorageRaceRegressionTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var rootDir: File
    private lateinit var noBackupDir: File
    private lateinit var sessionTracker: DefaultSessionTracker
    private lateinit var retentionAdapter: DefaultLocalRetentionAdapter

    @Before
    fun setUp() {
        rootDir = temporaryFolder.newFolder("retained-content")
        noBackupDir = temporaryFolder.newFolder("no_backup")
        sessionTracker = DefaultSessionTracker()
        retentionAdapter = DefaultLocalRetentionAdapter(
            root = rootDir,
            session = { sessionTracker.currentSession() }
        )
    }

    @After
    fun tearDown() {
        temporaryFolder.delete()
    }

    @Test
    fun testLateWriteRaceAcrossReauthenticationIsDenied() = runBlocking {
        sessionTracker.startSession("user-a")
        val gen1Session = sessionTracker.currentSession()

        // Create a gated retention adapter where saveRetainedContent pauses
        val gate = CompletableDeferred<Unit>()
        val gatedAdapter = object : LocalRetentionAdapter by retentionAdapter {
            override suspend fun saveRetainedContent(
                targetSession: SessionSnapshot,
                capsuleId: String,
                verifiedUnlockedAt: Instant,
                text: String?,
                photoBytes: ByteArray?,
                photoFormat: OriginalPhotoFormat?
            ): OperationResult<Unit> {
                gate.await()
                return retentionAdapter.saveRetainedContent(targetSession, capsuleId, verifiedUnlockedAt, text, photoBytes, photoFormat)
            }
        }

        // Start write in background under Gen 1
        val backgroundWrite = async(Dispatchers.IO) {
            gatedAdapter.saveRetainedContent(
                targetSession = gen1Session,
                capsuleId = "capsule-race-1",
                verifiedUnlockedAt = Instant.now(),
                text = "Late Write Text",
                photoBytes = null,
                photoFormat = null
            )
        }

        // Re-authenticate User A (Gen 1 -> logout -> Gen 2)
        sessionTracker.clearSession()
        sessionTracker.startSession("user-a")
        val gen2Session = sessionTracker.currentSession()
        assertTrue(gen2Session.sessionGeneration > gen1Session.sessionGeneration)

        // Resume the paused write
        gate.complete(Unit)
        val writeResult = backgroundWrite.await()

        // The late write from Gen 1 must be denied
        assertTrue("Late write must be denied", writeResult is OperationResult.Denied)

        // No files must be created for capsule-race-1
        val readRes = retentionAdapter.readRetainedContent(gen2Session, "capsule-race-1")
        assertTrue(readRes is OperationResult.Denied)
    }

    @Test
    fun onlineReadRetainsEveryValidContentCombinationForOfflineReview() = runBlocking {
        sessionTracker.startSession("user-recipient")
        val session = sessionTracker.currentSession()

        val png = imageFixture("png")
        var online = true
        val serverContent = mapOf(
            "text" to ProtectedCapsuleContent("text", "  message  ", null),
            "photo" to ProtectedCapsuleContent("photo", "", "remote/photo", png, OriginalPhotoFormat.PNG),
            "both" to ProtectedCapsuleContent("both", "caption", "remote/both", png, OriginalPhotoFormat.PNG),
            "no-cache" to ProtectedCapsuleContent("no-cache", "online only", null)
        )

        val fakeFirestore = object : FirestoreAdapter {
            override fun isAvailable(): Boolean = true
            override fun firestoreInstance(): FirebaseFirestore? = null
            override suspend fun getRecipientProfile(uid: String): OperationResult<RecipientProfile> =
                OperationResult.Success(RecipientProfile(uid, "Recipient Name"))
            override suspend fun saveRecipientProfile(profile: RecipientProfile): OperationResult<Unit> =
                OperationResult.Success(Unit)
            override suspend fun readRecipientUnlockRequirements(
                capsuleId: String,
                recipientUid: String
            ): OperationResult<RecipientUnlockRequirements> =
                if (!online) OperationResult.Unavailable("Offline") else OperationResult.Success(
                    RecipientUnlockRequirements(
                        capsuleId = capsuleId,
                        recipientUid = recipientUid,
                        unlocked = true,
                        lockMode = LockMode.TIME,
                        unlockAt = Instant.now().minusSeconds(100),
                        unlockedAt = Instant.now().minusSeconds(50)
                    )
                )
            override suspend fun readProtectedContent(
                capsuleId: String,
                recipientUid: String
            ): OperationResult<ProtectedCapsuleContent> =
                OperationResult.Success(serverContent.getValue(capsuleId))
        }

        val repository = DefaultCapsuleRepository(
            sessionTracker = sessionTracker,
            authAdapter = object : AuthAdapter {
                override fun activeUid(): String? = "user-recipient"
                override fun activeEmail(): String? = "user@example.com"
                override fun activeDisplayName(): String? = "Recipient"
                override suspend fun signIn(email: String, password: String): OperationResult<String> = OperationResult.Success("user-recipient")
                override suspend fun signUp(email: String, password: String, displayName: String): OperationResult<String> = OperationResult.Success("user-recipient")
                override suspend fun updateDisplayName(displayName: String): OperationResult<Unit> = OperationResult.Success(Unit)
                override fun signOut() {}
            },
            firestoreAdapter = fakeFirestore,
            retentionAdapter = retentionAdapter
        )

        for (id in listOf("text", "photo", "both")) {
            val read = repository.readContent(id, session) as OperationResult.Success
            assertTrue(read.data.availableOffline)
        }
        // A file in place of the directory forces an actual persistence failure.
        val accountDir = rootDir.listFiles()!!.single()
        File(accountDir, "no-cache").writeText("obstruction")
        val onlineOnly = repository.readContent("no-cache", session) as OperationResult.Success
        assertEquals("online only", onlineOnly.data.text)
        assertFalse(onlineOnly.data.availableOffline)

        online = false
        for (id in listOf("text", "photo", "both")) {
            val read = repository.readContent(id, session) as OperationResult.Success
            val expected = serverContent.getValue(id)
            assertEquals(expected.text?.takeIf { it.isNotBlank() }, read.data.text)
            org.junit.Assert.assertArrayEquals(expected.photoBytes, read.data.photoBytes)
            assertEquals(expected.photoFormat, read.data.photoFormat)
            assertTrue(read.data.availableOffline)
        }
        assertFalse(File(accountDir, "photo/content.text").exists())
        assertTrue(File(accountDir, "both/content.photo").delete())
        assertFalse(repository.readContent("both", session) is OperationResult.Success)
        // An intact, readable substitute still differs from the originally retained bytes.
        File(accountDir, "photo/content.photo").writeBytes(imageFixture("png", 0xffffff))
        assertFalse(repository.readContent("photo", session) is OperationResult.Success)
        assertTrue(File(accountDir, "text/content.text").delete())
        assertFalse(repository.readContent("text", session) is OperationResult.Success)
        assertFalse(repository.readContent("no-cache", session) is OperationResult.Success)
    }

    @Test
    fun testFailedPhotoDownloadForTextAndPhotoCapsuleFailsClosed() = runBlocking {
        sessionTracker.startSession("user-recipient-2")
        val session = sessionTracker.currentSession()

        val fakeFirestore = object : FirestoreAdapter {
            override fun isAvailable(): Boolean = true
            override fun firestoreInstance(): FirebaseFirestore? = null
            override suspend fun getRecipientProfile(uid: String): OperationResult<RecipientProfile> = OperationResult.Success(RecipientProfile(uid, "Recipient"))
            override suspend fun saveRecipientProfile(profile: RecipientProfile): OperationResult<Unit> = OperationResult.Success(Unit)
            override suspend fun readRecipientUnlockRequirements(capsuleId: String, recipientUid: String): OperationResult<RecipientUnlockRequirements> =
                OperationResult.Success(
                    RecipientUnlockRequirements(capsuleId, recipientUid, unlocked = true, lockMode = LockMode.TIME, unlockAt = Instant.now().minusSeconds(100), unlockedAt = Instant.now().minusSeconds(50))
                )
            override suspend fun readProtectedContent(capsuleId: String, recipientUid: String): OperationResult<ProtectedCapsuleContent> =
                OperationResult.Unavailable("Photo download failed due to network error.")
        }

        val repository = DefaultCapsuleRepository(
            sessionTracker = sessionTracker,
            authAdapter = object : AuthAdapter {
                override fun activeUid(): String? = "user-recipient-2"
                override fun activeEmail(): String? = "user2@example.com"
                override fun activeDisplayName(): String? = "Recipient 2"
                override suspend fun signIn(email: String, password: String): OperationResult<String> = OperationResult.Success("user-recipient-2")
                override suspend fun signUp(email: String, password: String, displayName: String): OperationResult<String> = OperationResult.Success("user-recipient-2")
                override suspend fun updateDisplayName(displayName: String): OperationResult<Unit> = OperationResult.Success(Unit)
                override fun signOut() {}
            },
            firestoreAdapter = fakeFirestore,
            retentionAdapter = retentionAdapter
        )

        val contentRes = repository.readContent("capsule-failed-photo", session)
        assertTrue("Read content must fail closed when photo download fails", contentRes is OperationResult.Unavailable)

        // Retention storage must NOT contain an incomplete text-only entry
        val offlineRes = retentionAdapter.readRetainedContent(sessionTracker.currentSession(), "capsule-failed-photo")
        assertTrue(offlineRes is OperationResult.Denied)
    }

    @Test
    fun testOldGenerationOfflineReadIsDeniedAfterReauthentication() = runBlocking {
        sessionTracker.startSession("user-a")
        val gen1Session = sessionTracker.currentSession()

        retentionAdapter.saveRetainedContent(
            targetSession = gen1Session,
            capsuleId = "capsule-gen1-read",
            verifiedUnlockedAt = Instant.now(),
            text = "Text Gen 1",
            photoBytes = null,
            photoFormat = null
        )

        // Logout and re-authenticate as User A (Gen 1 -> logout -> Gen 2)
        sessionTracker.clearSession()
        sessionTracker.startSession("user-a")

        // Offline read using Gen 1 targetSession MUST be denied
        val readGen1 = retentionAdapter.readRetainedContent(gen1Session, "capsule-gen1-read")
        assertTrue("Read using old generation must be denied", readGen1 is OperationResult.Denied)
    }

    @Test
    fun testInjectedDeletionFailureBlocksSessionActivation() = runBlocking {
        val failingAdapter = object : LocalRetentionAdapter by retentionAdapter {
            override suspend fun clearAccountData(uid: String) {
                throw IOException("Injected deletion failure during account cleanup")
            }
        }
        val orchestrator = AccountCleanupOrchestrator(
            retentionAdapter = failingAdapter,
            noBackupDir = noBackupDir
        )

        val cleanupRes = orchestrator.cleanupAccount("user-failing")
        assertTrue(cleanupRes is OperationResult.Unavailable)

        // Verify pending marker exists
        assertTrue(orchestrator.hasPendingCleanup())
    }

    @Test
    fun nativeDecoderValidatesDownloadedAndRetainedPhotoFormats() {
        for ((extension, format) in listOf("png" to OriginalPhotoFormat.PNG, "jpg" to OriginalPhotoFormat.JPEG)) {
            val bytes = imageFixture(extension)
            assertEquals(format, AndroidOriginalImageProbe.inspect(bytes))
            assertNull(AndroidOriginalImageProbe.inspect(bytes.copyOf(8)))
            assertNull(AndroidOriginalImageProbe.inspect(bytes.copyOf(bytes.size - 1)))
            assertNull(AndroidOriginalImageProbe.inspect(bytes.take(8).toByteArray() + bytes.takeLast(12).toByteArray()))
        }
    }
}

/** Produces complete image streams with a decoder independent of the Android code under test. */
internal fun imageFixture(format: String, color: Int = 0x112233): ByteArray {
    val image = java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB)
    image.setRGB(0, 0, color)
    return java.io.ByteArrayOutputStream().use {
        check(javax.imageio.ImageIO.write(image, format, it))
        it.toByteArray()
    }
}
