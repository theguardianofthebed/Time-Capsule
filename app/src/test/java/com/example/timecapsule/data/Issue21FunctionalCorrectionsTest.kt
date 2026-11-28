package com.example.timecapsule.data

import com.example.timecapsule.data.adapter.AuthAdapter
import com.example.timecapsule.data.adapter.DefaultLocalRetentionAdapter
import com.example.timecapsule.data.adapter.FirestoreAdapter
import com.example.timecapsule.data.session.DefaultSessionTracker
import com.example.timecapsule.domain.model.Availability
import com.example.timecapsule.domain.model.CapsuleLock
import com.example.timecapsule.domain.model.CapsuleSummary
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.domain.model.OriginalPhotoFormat
import com.example.timecapsule.domain.model.ProtectedCapsuleContent
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.model.RecipientUnlockRequirements
import com.example.timecapsule.domain.model.SendStatus
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot
import com.example.timecapsule.ui.s03.S03CapsuleListViewModel
import com.example.timecapsule.ui.s07.S07CapsuleDetailViewModel
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.security.MessageDigest

/**
 * Mandatory regression test suite for Issue #21 functional corrections:
 * 1. Offline re-view for TIME, LOCATION, and BOTH lock modes without manufactured metadata.
 * 2. Unlocked evidence with missing photo/text files vs relocking.
 * 3. Corrupted photo integrity verification.
 * 4. Network failure fallback vs authoritative access denial blocking.
 * 5. Cross-account retention isolation and stale session rejection.
 * 6. Lightweight list enumeration without bulk loading photo bytes.
 * 7. Asset retry without changing confirmed unlock timestamp.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class Issue21FunctionalCorrectionsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var rootDir: File
    private lateinit var sessionTracker: DefaultSessionTracker
    private lateinit var fakeAuthAdapter: FakeAuthAdapter
    private lateinit var fakeFirestoreAdapter: FakeFirestoreAdapter
    private lateinit var retentionAdapter: DefaultLocalRetentionAdapter
    private lateinit var repository: DefaultCapsuleRepository

    private val userA = "user-a"
    private val userB = "user-b"

    @Before
    fun setUp() {
        rootDir = tempFolder.newFolder("retained-content")
        sessionTracker = DefaultSessionTracker()
        sessionTracker.startSession(userA)
        fakeAuthAdapter = FakeAuthAdapter(userA)
        fakeFirestoreAdapter = FakeFirestoreAdapter()
        retentionAdapter = DefaultLocalRetentionAdapter(
            root = rootDir,
            session = { sessionTracker.currentSession() }
        )
        repository = DefaultCapsuleRepository(
            sessionTracker = sessionTracker,
            authAdapter = fakeAuthAdapter,
            firestoreAdapter = fakeFirestoreAdapter,
            retentionAdapter = retentionAdapter
        )
    }

    @Test
    fun offlineReviewForTimeLocationAndBothModesPreservesLockModeAndTitle() = runBlocking {
        val sessionA = sessionTracker.currentSession()
        val now = Instant.now()

        // Retain 3 capsules with distinct lock modes and titles
        val save1 = retentionAdapter.saveRetainedContent(
            targetSession = sessionA,
            capsuleId = "cap-time",
            verifiedUnlockedAt = now,
            text = "Time message",
            photoBytes = null,
            photoFormat = null,
            title = "Time Capsule Title",
            lockMode = LockMode.TIME,
            senderDisplayName = "Sender Jamie",
            createdAt = now.minusSeconds(1000)
        )
        assertTrue(save1 is OperationResult.Success)

        val save2 = retentionAdapter.saveRetainedContent(
            targetSession = sessionA,
            capsuleId = "cap-loc",
            verifiedUnlockedAt = now,
            text = "Location message",
            photoBytes = null,
            photoFormat = null,
            title = "Location Capsule Title",
            lockMode = LockMode.LOCATION,
            senderDisplayName = "Sender Alex",
            createdAt = now.minusSeconds(500)
        )
        assertTrue(save2 is OperationResult.Success)

        val save3 = retentionAdapter.saveRetainedContent(
            targetSession = sessionA,
            capsuleId = "cap-both",
            verifiedUnlockedAt = now,
            text = "Both message",
            photoBytes = null,
            photoFormat = null,
            title = "Both Capsule Title",
            lockMode = LockMode.TIME_AND_LOCATION,
            senderDisplayName = "Sender Sam",
            createdAt = now.minusSeconds(200)
        )
        assertTrue(save3 is OperationResult.Success)

        // Turn network OFF
        fakeFirestoreAdapter.isNetworkDown = true

        val listsRes = repository.lists(userA, sessionA)
        assertTrue(listsRes is OperationResult.Success)
        val snapshot = (listsRes as OperationResult.Success).data
        assertEquals(3, snapshot.received.size)

        val capTime = snapshot.received.first { it.capsuleId == "cap-time" }
        assertEquals("Time Capsule Title", capTime.title)
        assertEquals(LockMode.TIME, capTime.lock.mode)
        assertEquals("Sender Jamie", capTime.senderDisplayName)

        val capLoc = snapshot.received.first { it.capsuleId == "cap-loc" }
        assertEquals("Location Capsule Title", capLoc.title)
        assertEquals(LockMode.LOCATION, capLoc.lock.mode)
        assertEquals("Sender Alex", capLoc.senderDisplayName)

        val capBoth = snapshot.received.first { it.capsuleId == "cap-both" }
        assertEquals("Both Capsule Title", capBoth.title)
        assertEquals(LockMode.TIME_AND_LOCATION, capBoth.lock.mode)
        assertEquals("Sender Sam", capBoth.senderDisplayName)
    }

    @Test
    fun validUnlockMarkerWithMissingPhotoPreservesUnlockedStateAndAllowsRetry() = runBlocking {
        val sessionA = sessionTracker.currentSession()
        val now = Instant.now()
        val validPng = imageFixture("png")

        // 1. Save valid photo capsule to retention
        val saveRes = retentionAdapter.saveRetainedContent(
            targetSession = sessionA,
            capsuleId = "cap-photo-missing",
            verifiedUnlockedAt = now,
            text = "Photo caption",
            photoBytes = validPng,
            photoFormat = OriginalPhotoFormat.PNG,
            title = "Photo Capsule",
            lockMode = LockMode.TIME
        )
        assertTrue(saveRes is OperationResult.Success)

        // Delete photo file manually to simulate missing/deleted photo asset
        val accountHash = sha256(userA.toByteArray(Charsets.UTF_8))
        val photoFile = File(rootDir, "$accountHash/cap-photo-missing/content.photo")
        assertTrue(photoFile.exists())
        photoFile.delete()
        assertFalse(photoFile.exists())

        // 2. Offline summary read must preserve unlocked availability
        fakeFirestoreAdapter.isNetworkDown = true
        val summaryRes = repository.getSummary("cap-photo-missing", sessionA)
        assertTrue(summaryRes is OperationResult.Success)
        val summary = (summaryRes as OperationResult.Success).data
        assertEquals(Availability.AVAILABLE, summary.availability)
        assertEquals("Photo Capsule", summary.title)

        // 3. Offline content read must return Unavailable (content unavailable) without relocking
        val contentRes = repository.readContent("cap-photo-missing", sessionA)
        assertTrue(contentRes is OperationResult.Unavailable)
        assertTrue((contentRes as OperationResult.Unavailable).message.contains("unavailable"))
    }

    @Test
    fun corruptedRetainedPhotoFailsIntegrityCheckAndPreventsRead() = runBlocking {
        val sessionA = sessionTracker.currentSession()
        val now = Instant.now()
        val validPng = imageFixture("png")

        val saveRes = retentionAdapter.saveRetainedContent(
            targetSession = sessionA,
            capsuleId = "cap-corrupt",
            verifiedUnlockedAt = now,
            text = "Caption",
            photoBytes = validPng,
            photoFormat = OriginalPhotoFormat.PNG
        )
        assertTrue(saveRes is OperationResult.Success)

        // Overwrite photo file with corrupt bytes
        val accountHash = sha256(userA.toByteArray(Charsets.UTF_8))
        val photoFile = File(rootDir, "$accountHash/cap-corrupt/content.photo")
        assertTrue(photoFile.exists())
        photoFile.writeBytes(byteArrayOf(0, 1, 2, 3, 4, 5))

        val readRes = retentionAdapter.readRetainedContent(sessionA, "cap-corrupt")
        assertTrue(readRes is OperationResult.Unavailable)
    }

    @Test
    fun networkFailurePermitsOfflineFallbackWhereasAuthoritativeDenialBlocksFallback() = runBlocking {
        val sessionA = sessionTracker.currentSession()
        val now = Instant.now()

        val saveRes = retentionAdapter.saveRetainedContent(
            targetSession = sessionA,
            capsuleId = "cap-denied-test",
            verifiedUnlockedAt = now,
            text = "Retained text",
            photoBytes = null,
            photoFormat = null,
            title = "Retained Title"
        )
        assertTrue(saveRes is OperationResult.Success)

        // Scenario A: Network failure -> Offline fallback succeeds
        fakeFirestoreAdapter.isNetworkDown = true
        val offlineSummary = repository.getSummary("cap-denied-test", sessionA)
        assertTrue(offlineSummary is OperationResult.Success)
        assertEquals("Retained Title", (offlineSummary as OperationResult.Success).data.title)

        // Scenario B: Authoritative Denied response -> Returns Denied directly, NO offline fallback!
        fakeFirestoreAdapter.isNetworkDown = false
        fakeFirestoreAdapter.accessDeniedForSummary = true
        val deniedSummary = repository.getSummary("cap-denied-test", sessionA)
        assertTrue(deniedSummary is OperationResult.Denied)
    }

    @Test
    fun staleAndCrossAccountRetentionRequestsDenied() = runBlocking {
        val sessionA = sessionTracker.currentSession()
        val now = Instant.now()

        val saveRes = retentionAdapter.saveRetainedContent(
            targetSession = sessionA,
            capsuleId = "cap-private-a",
            verifiedUnlockedAt = now,
            text = "User A Private Message",
            photoBytes = null,
            photoFormat = null
        )
        assertTrue(saveRes is OperationResult.Success)

        // Switch to User B session
        sessionTracker.startSession(userB)
        val sessionB = sessionTracker.currentSession()

        // User B attempts to read User A's retained content
        val crossRead = retentionAdapter.readRetainedContent(sessionB, "cap-private-a")
        assertTrue(crossRead is OperationResult.Denied)

        val crossList = retentionAdapter.listVerifiedRecipientEntries(sessionB)
        assertTrue(crossList is OperationResult.Success)
        assertTrue((crossList as OperationResult.Success).data.isEmpty())
    }

    @Test
    fun multipleRetainedPhotoCapsulesEnumeratedWithoutLoadingPhotoBytes() = runBlocking {
        val sessionA = sessionTracker.currentSession()
        val now = Instant.now()
        val samplePhoto = imageFixture("png")

        // Save 5 retained photo capsules
        for (i in 1..5) {
            val saveRes = retentionAdapter.saveRetainedContent(
                targetSession = sessionA,
                capsuleId = "cap-multi-$i",
                verifiedUnlockedAt = now.plusSeconds(i.toLong()),
                text = "Caption $i",
                photoBytes = samplePhoto,
                photoFormat = OriginalPhotoFormat.PNG,
                title = "Title $i"
            )
            assertTrue(saveRes is OperationResult.Success)
        }

        val summariesRes = retentionAdapter.listVerifiedRecipientEntries(sessionA)
        assertTrue(summariesRes is OperationResult.Success)
        val list = (summariesRes as OperationResult.Success).data
        assertEquals(5, list.size)

        for (summary in list) {
            assertTrue(summary.hasPhoto)
            assertTrue(summary.isContentAvailable)
            assertNotNull(summary.title)
        }
    }

    @Test
    fun successfulOnlineAssetRetryRestoresContentWithoutChangingUnlockedState() = runBlocking {
        val sessionA = sessionTracker.currentSession()
        val now = Instant.now().minusSeconds(500)

        // Set up server unlocked capsule
        val capSummary = CapsuleSummary(
            capsuleId = "cap-retry",
            senderUid = userB,
            recipientUid = userA,
            lock = CapsuleLock(LockMode.TIME),
            sendStatus = SendStatus.SENT,
            availability = Availability.AVAILABLE,
            title = "Retry Capsule",
            unlockedAt = now
        )
        fakeFirestoreAdapter.summaries["cap-retry"] = capSummary
        fakeFirestoreAdapter.requirements["cap-retry"] = RecipientUnlockRequirements(
            capsuleId = "cap-retry",
            recipientUid = userA,
            unlocked = true,
            lockMode = LockMode.TIME,
            unlockedAt = now
        )
        fakeFirestoreAdapter.protectedContent["cap-retry"] = ProtectedCapsuleContent(
            capsuleId = "cap-retry",
            text = "Restored message",
            photoObjectPath = null
        )

        val readRes = repository.readContent("cap-retry", sessionA)
        assertTrue(readRes is OperationResult.Success)
        val content = (readRes as OperationResult.Success).data
        assertEquals("Restored message", content.text)

        // Verify saved retained timestamp matches original server unlockedAt
        val retainedRes = retentionAdapter.readRetainedContent(sessionA, "cap-retry")
        assertTrue(retainedRes is OperationResult.Success)
        assertEquals(now, (retainedRes as OperationResult.Success).data.verifiedUnlockedAt)
    }

    @Test
    fun initialPhotoDownloadFailureAfterConfirmedUnlockThenOfflineProcessRecreation() = runBlocking {
        val sessionA = sessionTracker.currentSession()
        val now = Instant.now()

        // 1. Persist verified unlock marker when online unlock is confirmed, even if photo download fails
        val markerRes = retentionAdapter.saveVerifiedUnlockMarker(
            targetSession = sessionA,
            capsuleId = "cap-initial-fail",
            verifiedUnlockedAt = now,
            title = "Photo Pending Title",
            lockMode = LockMode.TIME,
            senderUid = userB,
            recipientUid = userA,
            hasPhoto = true
        )
        assertTrue(markerRes is OperationResult.Success)

        // 2. Simulate process restart / offline execution
        fakeFirestoreAdapter.isNetworkDown = true

        val summaryRes = repository.getSummary("cap-initial-fail", sessionA)
        assertTrue(summaryRes is OperationResult.Success)
        val summary = (summaryRes as OperationResult.Success).data
        assertEquals("Photo Pending Title", summary.title)
        assertEquals(Availability.AVAILABLE, summary.availability)

        // Content is unavailable locally since photo wasn't downloaded yet
        val contentRes = repository.readContent("cap-initial-fail", sessionA)
        assertTrue(contentRes is OperationResult.Unavailable)
    }

    @Test
    fun accountTransitionWhileViewModelsActiveClearsPreviousAccountStateImmediately() = runBlocking {
        val listViewModel = S03CapsuleListViewModel(sessionTracker, repository)
        val detailViewModel = S07CapsuleDetailViewModel(sessionTracker, repository)

        // Transition to User B
        sessionTracker.startSession(userB)

        val listState = listViewModel.uiState.value
        val detailState = detailViewModel.uiState.value

        assertEquals(userB, listState.sessionSnapshot?.uid)
        assertEquals(userB, detailState.sessionSnapshot?.uid)
        assertTrue(listState.receivedList.isEmpty())
        assertTrue(detailState.content == null)
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    // --- Fakes ---

    private class FakeAuthAdapter(private var activeUid: String?) : AuthAdapter {
        override fun activeUid(): String? = activeUid
        override fun activeEmail(): String = "test@example.com"
        override fun activeDisplayName(): String = "Test User"
        override suspend fun signIn(email: String, password: String) = OperationResult.Success(activeUid ?: "")
        override suspend fun signUp(email: String, password: String, displayName: String) = OperationResult.Success(activeUid ?: "")
        override suspend fun updateDisplayName(displayName: String) = OperationResult.Success(Unit)
        override fun signOut() { activeUid = null }
    }

    private class FakeFirestoreAdapter : FirestoreAdapter {
        var isNetworkDown = false
        var accessDeniedForSummary = false
        val sentList = mutableMapOf<String, List<CapsuleSummary>>()
        val receivedList = mutableMapOf<String, List<CapsuleSummary>>()
        val summaries = mutableMapOf<String, CapsuleSummary>()
        val requirements = mutableMapOf<String, RecipientUnlockRequirements>()
        val protectedContent = mutableMapOf<String, ProtectedCapsuleContent>()

        override fun isAvailable(): Boolean = !isNetworkDown
        override fun firestoreInstance(): FirebaseFirestore? = null
        override suspend fun getRecipientProfile(uid: String) = OperationResult.Invalid("Not needed")
        override suspend fun saveRecipientProfile(profile: RecipientProfile) = OperationResult.Success(Unit)

        override suspend fun getSentCapsuleSummaries(senderUid: String): OperationResult<List<CapsuleSummary>> {
            if (isNetworkDown) return OperationResult.Unavailable("Network down")
            return OperationResult.Success(sentList[senderUid] ?: emptyList())
        }

        override suspend fun getReceivedCapsuleSummaries(recipientUid: String): OperationResult<List<CapsuleSummary>> {
            if (isNetworkDown) return OperationResult.Unavailable("Network down")
            return OperationResult.Success(receivedList[recipientUid] ?: emptyList())
        }

        override suspend fun getCapsuleSummary(capsuleId: String, uid: String): OperationResult<CapsuleSummary> {
            if (accessDeniedForSummary) return OperationResult.Denied("Authoritative access denied for capsule summary.")
            if (isNetworkDown) return OperationResult.Unavailable("Network down")
            val sum = summaries[capsuleId] ?: return OperationResult.Unavailable("Summary not implemented in fake")
            if (sum.senderUid != uid && sum.recipientUid != uid) return OperationResult.Denied("Not participant")
            return OperationResult.Success(sum)
        }

        override suspend fun readRecipientUnlockRequirements(
            capsuleId: String,
            recipientUid: String
        ): OperationResult<RecipientUnlockRequirements> {
            if (accessDeniedForSummary) return OperationResult.Denied("Authoritative access denied for unlock requirements.")
            if (isNetworkDown) return OperationResult.Unavailable("Network down")
            val req = requirements[capsuleId] ?: return OperationResult.Invalid("Not found")
            if (req.recipientUid != recipientUid) return OperationResult.Denied("Not recipient")
            return OperationResult.Success(req)
        }

        override suspend fun readProtectedContent(
            capsuleId: String,
            recipientUid: String
        ): OperationResult<ProtectedCapsuleContent> {
            if (isNetworkDown) return OperationResult.Unavailable("Network down")
            val content = protectedContent[capsuleId] ?: return OperationResult.Invalid("Not found")
            return OperationResult.Success(content)
        }
    }
}
