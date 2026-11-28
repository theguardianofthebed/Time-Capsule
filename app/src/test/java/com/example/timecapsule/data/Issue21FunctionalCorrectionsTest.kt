package com.example.timecapsule.data

import android.os.Looper
import com.example.timecapsule.data.adapter.AuthAdapter
import com.example.timecapsule.data.adapter.DefaultLocalRetentionAdapter
import com.example.timecapsule.data.adapter.FirestoreAdapter
import com.example.timecapsule.data.adapter.LocalRetentionAdapter
import com.example.timecapsule.data.recovery.ForegroundRecoveryCoordinator
import com.example.timecapsule.data.session.DefaultSessionTracker
import com.example.timecapsule.domain.model.ForegroundLocationSample
import com.example.timecapsule.domain.model.Availability
import com.example.timecapsule.domain.model.CapsuleDraft
import com.example.timecapsule.domain.model.CapsuleListsSnapshot
import com.example.timecapsule.domain.model.CapsuleLock
import com.example.timecapsule.domain.model.CapsuleSummary
import com.example.timecapsule.domain.model.ListProvenance
import com.example.timecapsule.domain.model.LocationTarget
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.domain.model.OriginalPhotoFormat
import com.example.timecapsule.domain.model.ProtectedCapsuleContent
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.model.RecipientUnlockRequirements
import com.example.timecapsule.domain.model.SendStatus
import com.example.timecapsule.domain.model.UnlockConfirmationKind
import com.example.timecapsule.domain.model.UnlockVerificationOutcome
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot
import com.example.timecapsule.ui.s03.S03CapsuleListViewModel
import com.example.timecapsule.ui.s07.DetailStatus
import com.example.timecapsule.ui.s07.S07CapsuleDetailViewModel
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Mandatory regression test suite for Issue #21 functional corrections.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class Issue21FunctionalCorrectionsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
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
        Dispatchers.setMain(testDispatcher)
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

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun offlineReviewForTimeLocationAndBothModesPreservesLockModeAndTitle() = runBlocking {
        val sessionA = sessionTracker.currentSession()
        val now = Instant.now()

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

        fakeFirestoreAdapter.isNetworkDown = true

        val listsRes = repository.lists(userA, sessionA)
        assertTrue(listsRes is OperationResult.Success)
        val snapshot = (listsRes as OperationResult.Success).data
        assertEquals(3, snapshot.received.size)

        val capTime = snapshot.received.first { it.capsuleId == "cap-time" }
        assertEquals("Time Capsule Title", capTime.title)
        assertEquals(LockMode.TIME, capTime.lock?.mode)
        assertEquals("Sender Jamie", capTime.senderDisplayName)

        val capLoc = snapshot.received.first { it.capsuleId == "cap-loc" }
        assertEquals("Location Capsule Title", capLoc.title)
        assertEquals(LockMode.LOCATION, capLoc.lock?.mode)
        assertEquals("Sender Alex", capLoc.senderDisplayName)

        val capBoth = snapshot.received.first { it.capsuleId == "cap-both" }
        assertEquals("Both Capsule Title", capBoth.title)
        assertEquals(LockMode.TIME_AND_LOCATION, capBoth.lock?.mode)
        assertEquals("Sender Sam", capBoth.senderDisplayName)
    }

    @Test
    fun validUnlockMarkerWithMissingPhotoPreservesUnlockedStateAndAllowsRetry() = runBlocking {
        val sessionA = sessionTracker.currentSession()
        val now = Instant.now()
        val validPng = imageFixture("png")

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

        val accountHash = sha256(userA.toByteArray(Charsets.UTF_8))
        val photoFile = File(rootDir, "$accountHash/cap-photo-missing/content.photo")
        assertTrue(photoFile.exists())
        photoFile.delete()
        assertFalse(photoFile.exists())

        fakeFirestoreAdapter.isNetworkDown = true
        val summaryRes = repository.getSummary("cap-photo-missing", sessionA)
        assertTrue(summaryRes is OperationResult.Success)
        val summary = (summaryRes as OperationResult.Success).data
        assertEquals(Availability.AVAILABLE, summary.availability)
        assertEquals("Photo Capsule", summary.title)

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

        fakeFirestoreAdapter.isNetworkDown = true
        val offlineSummary = repository.getSummary("cap-denied-test", sessionA)
        assertTrue(offlineSummary is OperationResult.Success)
        assertEquals("Retained Title", (offlineSummary as OperationResult.Success).data.title)

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

        sessionTracker.startSession(userB)
        val sessionB = sessionTracker.currentSession()

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

    /** A confirmed empty Sent query remains distinguishable from an unavailable Received query. */
    @Test fun tabsEmptySentAndUnavailableReceived() {
        fakeFirestoreAdapter.receivedUnavailable = true
        val remoteOnly = DefaultCapsuleRepository(sessionTracker, fakeAuthAdapter, fakeFirestoreAdapter)
        val model = S03CapsuleListViewModel(sessionTracker, remoteOnly)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(model.uiState.value.isSentUnavailable)
        assertTrue(model.uiState.value.isReceivedUnavailable)
    }

    /** A confirmed empty Received query remains distinguishable from an unavailable Sent query. */
    @Test fun tabsEmptyReceivedAndUnavailableSent() {
        fakeFirestoreAdapter.sentUnavailable = true
        val remoteOnly = DefaultCapsuleRepository(sessionTracker, fakeAuthAdapter, fakeFirestoreAdapter)
        val model = S03CapsuleListViewModel(sessionTracker, remoteOnly)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(model.uiState.value.isReceivedUnavailable)
        assertTrue(model.uiState.value.isSentUnavailable)
    }

    @Test
    fun timeLockVerificationReturnsLockedWaitingTime() = runBlocking {
        val now = Instant.now()
        fakeFirestoreAdapter.summaries["cap-time-locked"] = CapsuleSummary(
            "cap-time-locked", userB, userA, CapsuleLock(LockMode.TIME, now.plusSeconds(3600)),
            SendStatus.SENT, availability = Availability.LOCKED, title = "Time Locked"
        )
        fakeFirestoreAdapter.requirements["cap-time-locked"] = RecipientUnlockRequirements(
            "cap-time-locked", userA, unlocked = false, lockMode = LockMode.TIME, unlockAt = now.plusSeconds(3600)
        )
        fakeFirestoreAdapter.unlockVerificationResponse = OperationResult.Denied("Unlock authorization denied or unlock time not reached.")

        val fakeRepo = object : CapsuleRepository {
            override fun session() = sessionTracker.currentSession()
            override suspend fun getRecipient(uid: String) = OperationResult.Invalid("")
            override suspend fun publish(frozenOperation: CapsuleDraft) = OperationResult.Invalid("")
            override suspend fun reconcile(capsuleId: String) = OperationResult.Invalid("")
            override suspend fun lists(uid: String) = OperationResult.Invalid("")
            override suspend fun verifyUnlockOutcome(capsuleId: String, targetSession: SessionSnapshot) =
                repository.verifyUnlockOutcome(capsuleId, targetSession)
            override suspend fun getSummary(capsuleId: String, targetSession: SessionSnapshot): OperationResult<CapsuleSummary> {
                val sum = fakeFirestoreAdapter.summaries[capsuleId] ?: return OperationResult.Invalid("Not found")
                return OperationResult.Success(sum)
            }
            override suspend fun readContent(capsuleId: String) = readContent(capsuleId, sessionTracker.currentSession())
            override suspend fun readContent(capsuleId: String, targetSession: SessionSnapshot): OperationResult<ProtectedCapsuleContent> {
                val c = fakeFirestoreAdapter.protectedContent[capsuleId] ?: return OperationResult.Invalid("Not found")
                return OperationResult.Success(c)
            }
        }

        val fakeCoordinator = ForegroundRecoveryCoordinator(
            fakeRepo, sessionTracker, ioDispatcher = testDispatcher
        )
        val viewModel = S07CapsuleDetailViewModel(sessionTracker, fakeRepo, fakeCoordinator)
        viewModel.loadDetail("cap-time-locked")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isUnlocked)
        assertEquals(DetailStatus.LOCKED_WAITING_TIME, state.status)
        assertTrue(state.unlockReason?.contains("unlock time not reached", true) == true)

        // Verify subsequent successful verification works
        fakeFirestoreAdapter.unlockVerificationResponse = OperationResult.Success(
            UnlockVerificationOutcome(Availability.AVAILABLE, UnlockConfirmationKind.FIRST_UNLOCK_CONFIRMED)
        )
        fakeFirestoreAdapter.summaries["cap-time-locked"] = CapsuleSummary(
            "cap-time-locked", userB, userA, CapsuleLock(LockMode.TIME, now.plusSeconds(3600)),
            SendStatus.SENT, availability = Availability.AVAILABLE, title = "Time Unlocked"
        )
        fakeFirestoreAdapter.requirements["cap-time-locked"] = RecipientUnlockRequirements(
            "cap-time-locked", userA, unlocked = true, lockMode = LockMode.TIME, unlockAt = now.plusSeconds(3600), unlockedAt = now
        )
        fakeFirestoreAdapter.protectedContent["cap-time-locked"] = ProtectedCapsuleContent("cap-time-locked", "Unlocked Text", null)

        viewModel.loadDetail("cap-time-locked")
        testDispatcher.scheduler.advanceUntilIdle()

        val unlockedState = viewModel.uiState.value
        assertTrue(unlockedState.isUnlocked)
        assertEquals("Unlocked Text", unlockedState.content?.text)

        // Verify genuine non-participant denial remains ACCESS_DENIED
        fakeFirestoreAdapter.unlockVerificationResponse = OperationResult.Denied("Not participant")
        fakeFirestoreAdapter.summaries["cap-denied-user"] = CapsuleSummary(
            "cap-denied-user", userB, "other-user", CapsuleLock(LockMode.TIME), SendStatus.SENT, availability = Availability.LOCKED
        )
        fakeFirestoreAdapter.requirements["cap-denied-user"] = RecipientUnlockRequirements(
            "cap-denied-user", "other-user", unlocked = false, lockMode = LockMode.TIME
        )

        viewModel.loadDetail("cap-denied-user")
        testDispatcher.scheduler.advanceUntilIdle()

        val deniedState = viewModel.uiState.value
        assertEquals(DetailStatus.ACCESS_DENIED, deniedState.status)
    }

    @Test
    fun locationLockConditionDisplaysCoordinatesAndRadius() = runBlocking {
        val now = Instant.now()
        val locTarget = LocationTarget(latitude = 37.5665, longitude = 126.9780, radiusMeters = 150.0)
        fakeFirestoreAdapter.summaries["cap-loc-target"] = CapsuleSummary(
            "cap-loc-target", userB, userA, CapsuleLock(LockMode.LOCATION, now.plusSeconds(3600), location = locTarget),
            SendStatus.SENT, availability = Availability.LOCKED, title = "Location Locked"
        )
        fakeFirestoreAdapter.requirements["cap-loc-target"] = RecipientUnlockRequirements(
            "cap-loc-target", userA, unlocked = false, lockMode = LockMode.LOCATION, locationTarget = locTarget
        )

        val viewModel = S07CapsuleDetailViewModel(sessionTracker, repository)
        viewModel.loadDetail("cap-loc-target")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.summary)
        assertNotNull(state.summary?.lock?.location)
        assertEquals(37.5665, state.summary?.lock?.location?.latitude ?: 0.0, 0.0001)
        assertEquals(126.9780, state.summary?.lock?.location?.longitude ?: 0.0, 0.0001)
        assertEquals(150, state.summary?.lock?.location?.radiusMeters?.toInt())
    }

    @Test
    fun offlineSelfSendAppearsInBothTabs() = runBlocking {
        val sessionA = sessionTracker.currentSession()
        val now = Instant.now()

        // 1. Save A->A self-send with authoritative UIDs
        val saveSelf = retentionAdapter.saveRetainedContent(
            targetSession = sessionA,
            capsuleId = "cap-self-a-a",
            verifiedUnlockedAt = now,
            text = "Self Send Note",
            photoBytes = null,
            photoFormat = null,
            title = "Self Send Title",
            lockMode = LockMode.TIME,
            senderUid = userA,
            recipientUid = userA,
            senderDisplayName = "Me",
            recipientDisplayName = "Me"
        )
        assertTrue(saveSelf is OperationResult.Success)

        // 2. Save B->A received capsule
        val saveReceived = retentionAdapter.saveRetainedContent(
            targetSession = sessionA,
            capsuleId = "cap-recv-b-a",
            verifiedUnlockedAt = now,
            text = "B Note",
            photoBytes = null,
            photoFormat = null,
            title = "B Title",
            lockMode = LockMode.TIME,
            senderUid = userB,
            recipientUid = userA,
            senderDisplayName = "User B",
            recipientDisplayName = "Me"
        )
        assertTrue(saveReceived is OperationResult.Success)

        // 3. Save legacy record with unknown sender (senderUid = null)
        val saveLegacy = retentionAdapter.saveRetainedContent(
            targetSession = sessionA,
            capsuleId = "cap-legacy-unknown",
            verifiedUnlockedAt = now,
            text = "Legacy Note",
            photoBytes = null,
            photoFormat = null,
            title = "Legacy Title",
            lockMode = LockMode.TIME,
            senderUid = null,
            recipientUid = userA
        )
        assertTrue(saveLegacy is OperationResult.Success)

        // Force network offline
        fakeFirestoreAdapter.isNetworkDown = true

        val listsRes = repository.lists(userA, sessionA)
        assertTrue(listsRes is OperationResult.Success)
        val snapshot = (listsRes as OperationResult.Success).data

        // A->A must appear in Received AND Sent tabs with same capsule ID
        val selfInReceived = snapshot.received.firstOrNull { it.capsuleId == "cap-self-a-a" }
        val selfInSent = snapshot.sent.firstOrNull { it.capsuleId == "cap-self-a-a" }
        assertNotNull(selfInReceived)
        assertNotNull(selfInSent)
        assertEquals("cap-self-a-a", selfInReceived?.capsuleId)
        assertEquals("cap-self-a-a", selfInSent?.capsuleId)

        // B->A must appear ONLY in Received
        val bInReceived = snapshot.received.firstOrNull { it.capsuleId == "cap-recv-b-a" }
        val bInSent = snapshot.sent.firstOrNull { it.capsuleId == "cap-recv-b-a" }
        assertNotNull(bInReceived)
        assertNull(bInSent)

        // Legacy record with unknown sender must NOT be inserted into Sent
        val legacyInSent = snapshot.sent.firstOrNull { it.capsuleId == "cap-legacy-unknown" }
        assertNull(legacyInSent)

        // Server-confirmed query results when online are not overwritten
        fakeFirestoreAdapter.isNetworkDown = false
        fakeFirestoreAdapter.sentList[userA] = listOf(
            CapsuleSummary("cap-online-sent", userA, userB, CapsuleLock(LockMode.TIME), SendStatus.SENT, title = "Online Sent")
        )
        fakeFirestoreAdapter.receivedList[userA] = listOf(
            CapsuleSummary("cap-online-recv", userB, userA, CapsuleLock(LockMode.TIME), SendStatus.SENT, title = "Online Recv")
        )

        val onlineRes = repository.lists(userA, sessionA)
        assertTrue(onlineRes is OperationResult.Success)
        val onlineSnapshot = (onlineRes as OperationResult.Success).data
        assertEquals(ListProvenance.SERVER_CONFIRMED, onlineSnapshot.sentProvenance)
        assertEquals(ListProvenance.SERVER_CONFIRMED, onlineSnapshot.receivedProvenance)
        assertEquals(1, onlineSnapshot.sent.size)
        assertEquals("cap-online-sent", onlineSnapshot.sent[0].capsuleId)
    }

    @Test
    fun successfulOnlineAssetRetryRestoresContentWithoutChangingUnlockedState() = runBlocking {
        val sessionA = sessionTracker.currentSession()
        val now = Instant.now().minusSeconds(500)

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

        val retainedRes = retentionAdapter.readRetainedContent(sessionA, "cap-retry")
        assertTrue(retainedRes is OperationResult.Success)
        assertEquals(now, (retainedRes as OperationResult.Success).data.verifiedUnlockedAt)
    }

    @Test
    fun initialPhotoDownloadFailureAfterConfirmedUnlockThenOfflineProcessRecreation() = runBlocking {
        val session = sessionTracker.currentSession()
        val serverTime = Instant.parse("2026-10-15T08:00:00Z")

        val capSummary = CapsuleSummary(
            capsuleId = "cap-prod-fail",
            senderUid = userB,
            recipientUid = userA,
            lock = CapsuleLock(LockMode.TIME),
            sendStatus = SendStatus.SENT,
            availability = Availability.AVAILABLE,
            title = "Photo Production Fail",
            unlockedAt = serverTime,
            hasPhoto = true
        )
        fakeFirestoreAdapter.summaries["cap-prod-fail"] = capSummary
        fakeFirestoreAdapter.requirements["cap-prod-fail"] = RecipientUnlockRequirements(
            capsuleId = "cap-prod-fail",
            recipientUid = userA,
            unlocked = true,
            lockMode = LockMode.TIME,
            unlockedAt = serverTime
        )
        fakeFirestoreAdapter.protectedContent["cap-prod-fail"] = ProtectedCapsuleContent(
            capsuleId = "cap-prod-fail",
            text = "Text caption",
            photoObjectPath = "remote/photo.jpg",
            photoBytes = null // photo download fails!
        )

        // 1. Execute production repository readContent
        val initialRead = repository.readContent("cap-prod-fail", session)
        assertTrue(initialRead is OperationResult.Unavailable)

        // 2. Assert that the persisted marker timestamp equals the authoritative server timestamp
        val markerRes = retentionAdapter.listVerifiedRecipientEntries(session)
        assertTrue(markerRes is OperationResult.Success)
        val entries = (markerRes as OperationResult.Success).data
        assertEquals(1, entries.size)
        assertEquals(serverTime, entries[0].verifiedUnlockedAt)
        assertEquals("Photo Production Fail", entries[0].title)

        // 3. Recreate retention adapter & repository (simulating process recreation)
        val currentSnap = sessionTracker.currentSession()
        val newRetentionAdapter = DefaultLocalRetentionAdapter(
            root = rootDir,
            session = { sessionTracker.currentSession() }
        )
        val newRepository = DefaultCapsuleRepository(
            sessionTracker = sessionTracker,
            authAdapter = fakeAuthAdapter,
            firestoreAdapter = fakeFirestoreAdapter,
            retentionAdapter = newRetentionAdapter
        )

        // 4. Disconnect network
        fakeFirestoreAdapter.isNetworkDown = true

        // 5. Offline: unlocked entry is discoverable, but content is unavailable
        val offlineSummary = newRepository.getSummary("cap-prod-fail", currentSnap)
        assertTrue(offlineSummary is OperationResult.Success)
        assertEquals(Availability.AVAILABLE, (offlineSummary as OperationResult.Success).data.availability)

        val offlineContent = newRepository.readContent("cap-prod-fail", currentSnap)
        assertTrue(offlineContent is OperationResult.Unavailable)

        // 6. Reconnect network & fix photo download
        fakeFirestoreAdapter.isNetworkDown = false
        val validPng = imageFixture("png")
        fakeFirestoreAdapter.protectedContent["cap-prod-fail"] = ProtectedCapsuleContent(
            capsuleId = "cap-prod-fail",
            text = "Text caption",
            photoObjectPath = "remote/photo.jpg",
            photoBytes = validPng,
            photoFormat = OriginalPhotoFormat.PNG
        )

        // 7. Successful subsequent download
        val reconnectRead = newRepository.readContent("cap-prod-fail", currentSnap)
        assertTrue(reconnectRead is OperationResult.Success)
        val content = (reconnectRead as OperationResult.Success).data
        assertEquals("Text caption", content.text)
        assertNotNull(content.photoBytes)
    }

    @Test
    fun accountTransitionWhileViewModelsActiveClearsPreviousAccountStateImmediately() = runBlocking {
        val fakeRepo = object : CapsuleRepository {
            override fun session() = sessionTracker.currentSession()
            override suspend fun getRecipient(uid: String) = OperationResult.Invalid("")
            override suspend fun publish(frozenOperation: CapsuleDraft) = OperationResult.Invalid("")
            override suspend fun reconcile(capsuleId: String) = OperationResult.Invalid("")
            override suspend fun lists(uid: String) = lists(uid, sessionTracker.currentSession())
            override suspend fun lists(uid: String, targetSession: SessionSnapshot): OperationResult<CapsuleListsSnapshot> {
                val list = if (uid == userA) listOf(CapsuleSummary("cap-recv-a", userB, userA, CapsuleLock(LockMode.TIME), SendStatus.SENT, title = "Recv A", availability = Availability.AVAILABLE)) else emptyList()
                return OperationResult.Success(CapsuleListsSnapshot(sent = emptyList(), received = list))
            }
            override suspend fun getSummary(capsuleId: String) = getSummary(capsuleId, sessionTracker.currentSession())
            override suspend fun getSummary(capsuleId: String, targetSession: SessionSnapshot): OperationResult<CapsuleSummary> {
                if (targetSession.uid != userA) return OperationResult.Denied("Not user A")
                return OperationResult.Success(CapsuleSummary("cap-recv-a", userB, userA, CapsuleLock(LockMode.TIME), SendStatus.SENT, title = "Recv A", availability = Availability.AVAILABLE))
            }
            override suspend fun readContent(capsuleId: String) = readContent(capsuleId, sessionTracker.currentSession())
            override suspend fun readContent(capsuleId: String, targetSession: SessionSnapshot): OperationResult<ProtectedCapsuleContent> {
                if (targetSession.uid != userA) return OperationResult.Denied("Not user A")
                return OperationResult.Success(ProtectedCapsuleContent("cap-recv-a", "User A Secret", null))
            }
        }

        val listViewModel = S03CapsuleListViewModel(sessionTracker, fakeRepo)
        val detailViewModel = S07CapsuleDetailViewModel(sessionTracker, fakeRepo)

        listViewModel.loadLists()
        detailViewModel.loadDetail("cap-recv-a")

        testDispatcher.scheduler.advanceUntilIdle()

        // Assert User A's data was loaded
        assertEquals(1, listViewModel.uiState.value.receivedList.size)
        assertEquals("User A Secret", detailViewModel.uiState.value.content?.text)

        // Transition to User B
        sessionTracker.startSession(userB)

        testDispatcher.scheduler.advanceUntilIdle()

        val listState = listViewModel.uiState.value
        val detailState = detailViewModel.uiState.value

        assertEquals(userB, listState.sessionSnapshot?.uid)
        assertEquals(userB, detailState.sessionSnapshot?.uid)
        assertTrue(listState.receivedList.isEmpty())
        assertTrue(detailState.content == null)
    }

    @Test
    fun interruptedRetainedContentReplacementPreservesUnlockEvidenceWithoutAuthorizingIncompleteBytes() = runBlocking {
        val sessionA = sessionTracker.currentSession()
        val now = Instant.now().minusSeconds(1000)
        val initialPng = imageFixture("png")

        // 1. Save a valid complete text-and-photo capsule using real DefaultLocalRetentionAdapter
        val initialSave = retentionAdapter.saveRetainedContent(
            targetSession = sessionA,
            capsuleId = "cap-interrupt",
            verifiedUnlockedAt = now,
            text = "Initial Text",
            photoBytes = initialPng,
            photoFormat = OriginalPhotoFormat.PNG,
            title = "Initial Title",
            lockMode = LockMode.TIME,
            senderDisplayName = "User B",
            recipientDisplayName = "User A"
        )
        assertTrue(initialSave is OperationResult.Success)

        val initialRead = retentionAdapter.readRetainedContent(sessionA, "cap-interrupt")
        assertTrue(initialRead is OperationResult.Success)

        // 2 & 3. Inject I/O fault into production DefaultLocalRetentionAdapter seam AFTER commitment invalidation
        retentionAdapter.faultInjector = {
            throw IOException("Forced I/O fault mid-replacement after commitment invalidation!")
        }

        val updatedPng = imageFixture("png")
        val interruptedSave = retentionAdapter.saveRetainedContent(
            targetSession = sessionA,
            capsuleId = "cap-interrupt",
            verifiedUnlockedAt = now,
            text = "New Replacement Text",
            photoBytes = updatedPng,
            photoFormat = OriginalPhotoFormat.PNG,
            title = "Replacement Title",
            lockMode = LockMode.TIME
        )
        assertTrue(interruptedSave is OperationResult.Unavailable)

        // 4. Recreate retention adapter (simulating process restart)
        val newRetentionAdapter = DefaultLocalRetentionAdapter(
            root = rootDir,
            session = { sessionTracker.currentSession() }
        )

        // 5. Confirm unlock evidence survives, but reading incomplete content fails closed!
        val summaryRes = newRetentionAdapter.listVerifiedRecipientEntries(sessionA)
        assertTrue(summaryRes is OperationResult.Success)
        val summaries = (summaryRes as OperationResult.Success).data
        assertEquals(1, summaries.size)
        assertEquals(now, summaries[0].verifiedUnlockedAt)
        assertFalse(summaries[0].isContentAvailable) // Content NOT available due to invalidation!

        val contentRead = newRetentionAdapter.readRetainedContent(sessionA, "cap-interrupt")
        assertTrue(contentRead is OperationResult.Denied || contentRead is OperationResult.Unavailable)

        // 6 & 7. Refresh verified unlock evidence alone without content files
        val refreshMarker = newRetentionAdapter.saveVerifiedUnlockMarker(
            targetSession = sessionA,
            capsuleId = "cap-interrupt",
            verifiedUnlockedAt = now,
            title = "Replacement Title",
            lockMode = LockMode.TIME,
            hasPhoto = true
        )
        assertTrue(refreshMarker is OperationResult.Success)

        // Confirm refresh does NOT reactivate incomplete bytes!
        val refreshSummaries = (newRetentionAdapter.listVerifiedRecipientEntries(sessionA) as OperationResult.Success).data
        assertFalse(refreshSummaries[0].isContentAvailable)
        val refreshedRead = newRetentionAdapter.readRetainedContent(sessionA, "cap-interrupt")
        assertTrue(refreshedRead is OperationResult.Denied || refreshedRead is OperationResult.Unavailable)

        // 8. Clear fault injector, perform a successful authorized download and confirm offline re-view works
        retentionAdapter.faultInjector = null
        val cleanSave = retentionAdapter.saveRetainedContent(
            targetSession = sessionA,
            capsuleId = "cap-interrupt",
            verifiedUnlockedAt = now,
            text = "Final Replacement Text",
            photoBytes = updatedPng,
            photoFormat = OriginalPhotoFormat.PNG,
            title = "Replacement Title",
            lockMode = LockMode.TIME
        )
        assertTrue(cleanSave is OperationResult.Success)

        val finalRead = newRetentionAdapter.readRetainedContent(sessionA, "cap-interrupt")
        assertTrue(finalRead is OperationResult.Success)
        assertEquals("Final Replacement Text", (finalRead as OperationResult.Success).data.text)
    }

    @Test
    fun refreshingUnlockEvidenceForCommittedCapsulePreservesBothAssets() = runBlocking {
        val sessionA = sessionTracker.currentSession()
        val now = Instant.now().minusSeconds(500)
        val validPng = imageFixture("png")

        // 1. Save valid text and photo capsule
        val saveRes = retentionAdapter.saveRetainedContent(
            targetSession = sessionA,
            capsuleId = "cap-committed-refresh",
            verifiedUnlockedAt = now,
            text = "Committed Text",
            photoBytes = validPng,
            photoFormat = OriginalPhotoFormat.PNG,
            title = "Committed Title",
            lockMode = LockMode.TIME
        )
        assertTrue(saveRes is OperationResult.Success)

        // 2. Refresh unlock evidence with default hasText = false, hasPhoto = false
        val refreshRes = retentionAdapter.saveVerifiedUnlockMarker(
            targetSession = sessionA,
            capsuleId = "cap-committed-refresh",
            verifiedUnlockedAt = now,
            title = "Refreshed Title",
            lockMode = LockMode.TIME
        )
        assertTrue(refreshRes is OperationResult.Success)

        // 3. Disconnect network and recreate repository & retention adapter
        val newRetentionAdapter = DefaultLocalRetentionAdapter(
            root = rootDir,
            session = { sessionTracker.currentSession() }
        )
        val newRepository = DefaultCapsuleRepository(
            sessionTracker = sessionTracker,
            authAdapter = fakeAuthAdapter,
            firestoreAdapter = fakeFirestoreAdapter,
            retentionAdapter = newRetentionAdapter
        )
        fakeFirestoreAdapter.isNetworkDown = true

        // 4. Verify BOTH assets (text and photo) remain readable offline
        val offlineRead = newRepository.readContent("cap-committed-refresh", sessionA)
        assertTrue(offlineRead is OperationResult.Success)
        val content = (offlineRead as OperationResult.Success).data
        assertEquals("Committed Text", content.text)
        assertNotNull(content.photoBytes)
        assertEquals(OriginalPhotoFormat.PNG, content.photoFormat)
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
        var sentUnavailable = false
        var receivedUnavailable = false
        var accessDeniedForSummary = false
        var contentFailure: OperationResult<Nothing>? = null
        var unlockVerificationResponse: OperationResult<UnlockVerificationOutcome>? = null
        val sentList = mutableMapOf<String, List<CapsuleSummary>>()
        val receivedList = mutableMapOf<String, List<CapsuleSummary>>()
        val summaries = mutableMapOf<String, CapsuleSummary>()
        val requirements = mutableMapOf<String, RecipientUnlockRequirements>()
        val protectedContent = mutableMapOf<String, ProtectedCapsuleContent>()

        override fun isAvailable(): Boolean = !isNetworkDown
        override fun firestoreInstance(): FirebaseFirestore? = null
        override suspend fun getRecipientProfile(uid: String) = OperationResult.Invalid("Not needed")
        override suspend fun saveRecipientProfile(profile: RecipientProfile) = OperationResult.Success(Unit)

        override suspend fun verifyRecipientUnlockDetailed(
            capsuleId: String,
            recipientUid: String,
            locationSample: ForegroundLocationSample?,
            clockNanosSupplier: () -> Long
        ): OperationResult<UnlockVerificationOutcome> {
            if (isNetworkDown) return OperationResult.Unavailable("Network down")
            unlockVerificationResponse?.let { return it }
            return OperationResult.Success(UnlockVerificationOutcome(Availability.AVAILABLE, UnlockConfirmationKind.ALREADY_UNLOCKED))
        }

        override suspend fun getSentCapsuleSummaries(senderUid: String): OperationResult<List<CapsuleSummary>> {
            if (isNetworkDown || sentUnavailable) return OperationResult.Unavailable("Network down")
            return OperationResult.Success(sentList[senderUid] ?: emptyList())
        }

        override suspend fun getReceivedCapsuleSummaries(recipientUid: String): OperationResult<List<CapsuleSummary>> {
            if (isNetworkDown || receivedUnavailable) return OperationResult.Unavailable("Network down")
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
            contentFailure?.let { return it }
            val content = protectedContent[capsuleId] ?: return OperationResult.Invalid("Not found")
            return OperationResult.Success(content)
        }
    }
}
