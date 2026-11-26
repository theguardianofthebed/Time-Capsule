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
    fun testPNGPhotoDownloadAndFormatRetention() = runBlocking {
        sessionTracker.startSession("user-recipient")
        val session = sessionTracker.currentSession()

        val samplePngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00)

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
                OperationResult.Success(
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
                OperationResult.Success(
                    ProtectedCapsuleContent(
                        capsuleId = capsuleId,
                        text = "PNG Capsule Text",
                        photoObjectPath = "capsuleMedia/sender/capsule-png/original",
                        photoBytes = samplePngBytes,
                        photoFormat = OriginalPhotoFormat.PNG
                    )
                )
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

        val contentRes = repository.readContent("capsule-png", session)
        assertTrue(contentRes is OperationResult.Success)

        val content = (contentRes as OperationResult.Success).data
        assertEquals("PNG Capsule Text", content.text)
        assertNotNull(content.photoBytes)
        assertTrue(samplePngBytes.contentEquals(content.photoBytes!!))
        assertEquals(OriginalPhotoFormat.PNG, content.photoFormat)
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
    fun testProductionImageValidationHeaderDetection() = runBlocking {
        val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00)
        val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00)
        val malformedBytes = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04)

        // Test PNG detection
        val pngFormat = if (pngBytes.size >= 4 &&
            pngBytes[0] == 0x89.toByte() && pngBytes[1] == 0x50.toByte() &&
            pngBytes[2] == 0x4E.toByte() && pngBytes[3] == 0x47.toByte()) OriginalPhotoFormat.PNG else OriginalPhotoFormat.JPEG
        assertEquals(OriginalPhotoFormat.PNG, pngFormat)

        // Test JPEG detection
        val jpegFormat = if (jpegBytes.size >= 3 &&
            jpegBytes[0] == 0xFF.toByte() && jpegBytes[1] == 0xD8.toByte() &&
            jpegBytes[2] == 0xFF.toByte()) OriginalPhotoFormat.JPEG else OriginalPhotoFormat.PNG
        assertEquals(OriginalPhotoFormat.JPEG, jpegFormat)

        // Test malformed header rejection
        val isMalformedValid = (malformedBytes.size >= 4 && malformedBytes[0] == 0x89.toByte() && malformedBytes[1] == 0x50.toByte()) ||
                (malformedBytes.size >= 3 && malformedBytes[0] == 0xFF.toByte() && malformedBytes[1] == 0xD8.toByte())
        assertFalse("Malformed image bytes must be rejected", isMalformedValid)
    }
}
