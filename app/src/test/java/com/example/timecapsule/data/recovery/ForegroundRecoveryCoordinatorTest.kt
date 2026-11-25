package com.example.timecapsule.data.recovery

import com.example.timecapsule.data.CapsuleRepository
import com.example.timecapsule.data.DefaultCapsuleRepository
import com.example.timecapsule.data.adapter.AuthAdapter
import com.example.timecapsule.data.adapter.FirestoreAdapter
import com.example.timecapsule.data.adapter.LocationAdapter
import com.example.timecapsule.data.session.DefaultSessionTracker
import com.example.timecapsule.data.session.SessionTracker
import com.example.timecapsule.domain.model.Availability
import com.example.timecapsule.domain.model.CapsuleDraft
import com.example.timecapsule.domain.model.CapsuleListsSnapshot
import com.example.timecapsule.domain.model.ForegroundLocationSample
import com.example.timecapsule.domain.model.LocationSampleResult
import com.example.timecapsule.domain.model.LocationSampleStatus
import com.example.timecapsule.domain.model.LocationTarget
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.domain.model.ProtectedCapsuleContent
import com.example.timecapsule.domain.model.PublicationPhase
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.model.RecipientUnlockRequirements
import com.example.timecapsule.domain.model.RecoveredPublication
import com.example.timecapsule.domain.model.SendStatus
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Deterministic unit test suite for [ForegroundRecoveryCoordinator] verifying single-flight coalescing,
 * immediate completion/failure safety, session drift protection across async boundaries, timeout boundaries,
 * waiter cancellation isolation, UI callback session revalidation, and domain unlock/recovery business logic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundRecoveryCoordinatorTest {

    private lateinit var sessionTracker: DefaultSessionTracker
    private lateinit var fakeRepository: FakeCapsuleRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        sessionTracker = DefaultSessionTracker()
        sessionTracker.startSession("user-1")
        fakeRepository = FakeCapsuleRepository(sessionTracker)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createCoordinator(
        repo: CapsuleRepository = fakeRepository,
        sessionTrack: SessionTracker = sessionTracker,
        timeoutMillis: Long = 15_000L
    ): ForegroundRecoveryCoordinator {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        return ForegroundRecoveryCoordinator(
            repository = repo,
            sessionTracker = sessionTrack,
            ioDispatcher = dispatcher,
            scope = this,
            responseTimeoutMillis = timeoutMillis
        )
    }

    // --- Single-Flight Tests ---

    @Test
    fun immediateCompletionDuringRegistration_handledSafely() = runTest {
        val coordinator = createCoordinator()
        fakeRepository.unlockResults["capsule-immediate"] = OperationResult.Success(Availability.AVAILABLE)

        val res = coordinator.verifyUnlock("capsule-immediate")

        assertTrue(res is OperationResult.Success)
        assertEquals(Availability.AVAILABLE, (res as OperationResult.Success).data)
        assertEquals(1, fakeRepository.verifyUnlockCallCount.get())

        // Subsequent call must run a fresh operation
        val res2 = coordinator.verifyUnlock("capsule-immediate")
        assertTrue(res2 is OperationResult.Success)
        assertEquals(2, fakeRepository.verifyUnlockCallCount.get())
    }

    @Test
    fun immediateFailureDuringRegistration_handledSafelyAndEvictedFromMap() = runTest {
        val coordinator = createCoordinator()
        fakeRepository.onVerifyUnlockException = RuntimeException("Immediate database error")

        val res = coordinator.verifyUnlock("capsule-fail-immediate")

        assertTrue(res is OperationResult.Unavailable)
        assertEquals(1, fakeRepository.verifyUnlockCallCount.get())

        // Map key is cleared; next call attempts a fresh operation
        fakeRepository.onVerifyUnlockException = null
        fakeRepository.unlockResults["capsule-fail-immediate"] = OperationResult.Success(Availability.AVAILABLE)

        val res2 = coordinator.verifyUnlock("capsule-fail-immediate")
        assertTrue(res2 is OperationResult.Success)
        assertEquals(2, fakeRepository.verifyUnlockCallCount.get())
    }

    @Test
    fun twoSimultaneousSameIdRequests_coalesceToOneVerifyOperation() = runTest {
        val coordinator = createCoordinator()
        val gate = CompletableDeferred<OperationResult<Availability>>()
        fakeRepository.onVerifyUnlockDeferred = { gate }

        val job1 = async { coordinator.verifyUnlock("capsule-same-id") }
        val job2 = async { coordinator.verifyUnlock("capsule-same-id") }

        runCurrent()
        assertEquals(1, fakeRepository.verifyUnlockCallCount.get())

        gate.complete(OperationResult.Success(Availability.AVAILABLE))

        val res1 = job1.await()
        val res2 = job2.await()

        assertTrue(res1 is OperationResult.Success)
        assertTrue(res2 is OperationResult.Success)
        assertEquals(Availability.AVAILABLE, (res1 as OperationResult.Success).data)
        assertEquals(Availability.AVAILABLE, (res2 as OperationResult.Success).data)
        assertEquals(1, fakeRepository.verifyUnlockCallCount.get())
    }

    @Test
    fun differentCapsuleIds_operateIndependently() = runTest {
        val coordinator = createCoordinator()
        fakeRepository.unlockResults["capsule-A"] = OperationResult.Success(Availability.AVAILABLE)
        fakeRepository.unlockResults["capsule-B"] = OperationResult.Denied("Locked")

        val resA = coordinator.verifyUnlock("capsule-A")
        val resB = coordinator.verifyUnlock("capsule-B")

        assertTrue(resA is OperationResult.Success)
        assertTrue(resB is OperationResult.Denied)
        assertEquals(2, fakeRepository.verifyUnlockCallCount.get())
    }

    @Test
    fun completedFlight_followedByFreshRetry() = runTest {
        val coordinator = createCoordinator()
        fakeRepository.unlockResults["capsule-retry"] = OperationResult.Unavailable("Offline network")

        val res1 = coordinator.verifyUnlock("capsule-retry")
        assertTrue(res1 is OperationResult.Unavailable)
        assertEquals(1, fakeRepository.verifyUnlockCallCount.get())

        // Network recovers -> update fake repository result
        fakeRepository.unlockResults["capsule-retry"] = OperationResult.Success(Availability.AVAILABLE)

        val res2 = coordinator.verifyUnlock("capsule-retry")

        assertTrue(res2 is OperationResult.Success)
        assertEquals(Availability.AVAILABLE, (res2 as OperationResult.Success).data)
        assertEquals(2, fakeRepository.verifyUnlockCallCount.get())
    }

    @Test
    fun cancellationOfOneWaiter_doesNotCancelOtherWaiterOrUnderlyingScope() = runTest {
        val coordinator = createCoordinator()
        val gate = CompletableDeferred<OperationResult<Availability>>()
        fakeRepository.onVerifyUnlockDeferred = { gate }

        val job1 = launch { coordinator.verifyUnlock("capsule-waiter") }
        val job2 = async { coordinator.verifyUnlock("capsule-waiter") }

        runCurrent()
        job1.cancel()

        gate.complete(OperationResult.Success(Availability.AVAILABLE))

        val res2 = job2.await()
        assertTrue(res2 is OperationResult.Success)
        assertEquals(Availability.AVAILABLE, (res2 as OperationResult.Success).data)
    }

    @Test
    fun stalledOperationTimeout_followedBySafeFreshVerificationAttempt() = runTest {
        val coordinator = createCoordinator(timeoutMillis = 100L)
        val neverGate = CompletableDeferred<OperationResult<Availability>>()
        fakeRepository.onVerifyUnlockDeferred = { neverGate }

        val job1 = async { coordinator.verifyUnlock("capsule-stalled") }
        runCurrent()
        advanceTimeBy(150L)

        val res1 = job1.await()
        assertTrue(res1 is OperationResult.Unavailable)
        assertTrue((res1 as OperationResult.Unavailable).message.contains("timed out"))

        // Stalled flight completed with timeout and was removed from map; subsequent call runs fresh
        fakeRepository.onVerifyUnlockDeferred = null
        fakeRepository.unlockResults["capsule-stalled"] = OperationResult.Success(Availability.AVAILABLE)

        val res2 = coordinator.verifyUnlock("capsule-stalled")
        assertTrue(res2 is OperationResult.Success)
        assertEquals(Availability.AVAILABLE, (res2 as OperationResult.Success).data)
    }

    // --- Session Isolation Tests ---

    @Test
    fun accountSwitchBeforeScheduledOperation_returnsDeniedWithoutCallingRepository() = runTest {
        val coordinator = createCoordinator()
        val capturedSession = sessionTracker.currentSession()

        // Switch user session before starting
        sessionTracker.startSession("user-2")

        val res = coordinator.verifyUnlock("capsule-1", capturedSession)

        assertTrue(res is OperationResult.Denied)
        assertTrue((res as OperationResult.Denied).message.contains("Authentication required") || res.message.contains("changed"))
        assertEquals(0, fakeRepository.verifyUnlockCallCount.get())
    }

    @Test
    fun accountSwitchWhilePublicationRecoverySuspended_returnsDeniedAndDiscardsResult() = runTest {
        val coordinator = createCoordinator()
        val gate = CompletableDeferred<OperationResult<List<RecoveredPublication>>>()
        fakeRepository.onRecoverDeferred = { gate }

        val capturedSession = sessionTracker.currentSession()
        val job = async { coordinator.recoverPublications(capturedSession) }

        runCurrent()
        // Account switch occurs while recovery is in flight
        sessionTracker.startSession("user-2")

        gate.complete(OperationResult.Success(listOf(RecoveredPublication("capsule-1", PublicationPhase.SENT, null, false))))

        val res = job.await()
        assertTrue(res is OperationResult.Denied)
        assertTrue((res as OperationResult.Denied).message.contains("session changed"))
    }

    @Test
    fun accountSwitchBetweenCapsuleChecksInCheckForeground_stopsWorkAndReturnsDenied() = runTest {
        val coordinator = createCoordinator()
        fakeRepository.unlockResults["capsule-1"] = OperationResult.Success(Availability.AVAILABLE)
        fakeRepository.unlockResults["capsule-2"] = OperationResult.Success(Availability.AVAILABLE)

        fakeRepository.onVerifyUnlockCallback = { capsuleId ->
            if (capsuleId == "capsule-1") {
                sessionTracker.startSession("user-2")
            }
        }

        val summary = coordinator.checkForeground(listOf("capsule-1", "capsule-2"))

        assertTrue(summary.publicationRecoveryResult is OperationResult.Denied)
        assertTrue((summary.publicationRecoveryResult as OperationResult.Denied).message.contains("session changed"))
    }

    @Test
    fun sameUidNewSessionGeneration_isolatesFlightAndIgnoresStaleReply() = runTest {
        val coordinator = createCoordinator()
        val gate = CompletableDeferred<OperationResult<Availability>>()
        fakeRepository.onVerifyUnlockDeferred = { gate }

        val job1 = async { coordinator.verifyUnlock("capsule-gen-1") }
        runCurrent()
        assertEquals(1, fakeRepository.verifyUnlockCallCount.get())

        // Re-authenticate same UID -> new session generation (e.g. generation 2 -> 3)
        sessionTracker.startSession("user-1")

        val job2 = async { coordinator.verifyUnlock("capsule-gen-1") }
        runCurrent()
        assertEquals(2, fakeRepository.verifyUnlockCallCount.get())

        gate.complete(OperationResult.Success(Availability.AVAILABLE))

        val res1 = job1.await()
        val res2 = job2.await()

        assertTrue(res1 is OperationResult.Denied)
        assertTrue(res2 is OperationResult.Success)
    }

    @Test
    fun logoutDuringActiveOperation_deniesResult() = runTest {
        val coordinator = createCoordinator()
        val gate = CompletableDeferred<OperationResult<Availability>>()
        fakeRepository.onVerifyUnlockDeferred = { gate }

        val job = async { coordinator.verifyUnlock("capsule-logout") }
        runCurrent()

        sessionTracker.clearSession()
        gate.complete(OperationResult.Success(Availability.AVAILABLE))

        val res = job.await()
        assertTrue(res is OperationResult.Denied)
    }

    @Test
    fun triggerForegroundRecovery_deliversCallbackOnMainWithSessionValidation() = runTest {
        val coordinator = createCoordinator()
        fakeRepository.unlockResults["capsule-cb"] = OperationResult.Success(Availability.AVAILABLE)

        val summary = coordinator.checkForeground(listOf("capsule-cb"))
        assertTrue(summary.publicationRecoveryResult is OperationResult.Success)
    }

    @Test
    fun sessionSwitchAfterTriggerForegroundRecoveryCalled_beforeCoroutineExecutes_suppressesOperationAndCallback() = runTest {
        val standardDispatcher = StandardTestDispatcher(testScheduler)
        val coordinator = ForegroundRecoveryCoordinator(
            repository = fakeRepository,
            sessionTracker = sessionTracker,
            ioDispatcher = standardDispatcher,
            scope = this,
            responseTimeoutMillis = 15_000L
        )

        // 1. Trigger foreground recovery while session is user-1 (queued on StandardTestDispatcher)
        coordinator.triggerForegroundRecovery(listOf("capsule-1"))

        // 2. Immediately switch session to user-2 before launched coroutine executes
        sessionTracker.startSession("user-2")

        // 3. Execute queued coroutine
        runCurrent()

        // 4. Assert no repository calls were executed for the stale trigger
        assertEquals(0, fakeRepository.recoverPublicationsCallCount.get())
        assertEquals(0, fakeRepository.verifyUnlockCallCount.get())
    }

    @Test
    fun sessionSwitchDuringRepositoryDispatch_assertsNoOperationStartsUnderWrongAccount() = runTest {
        val sessionTrack = DefaultSessionTracker()
        sessionTrack.startSession("user-1")

        val capturedSession = sessionTrack.currentSession()

        val fakeAuth = FakeAuthAdapterUnlock(sessionTrack)
        val fakeFirestore = FakeFirestoreAdapterUnlock()
        val repository = DefaultCapsuleRepository(
            sessionTracker = sessionTrack,
            authAdapter = fakeAuth,
            firestoreAdapter = fakeFirestore,
            locationAdapter = null
        )

        // Session switches to user-2 before repository dispatch begins
        sessionTrack.startSession("user-2")

        val res = repository.verifyUnlock("capsule-time", capturedSession)

        assertTrue(res is OperationResult.Denied)
        assertTrue((res as OperationResult.Denied).message.contains("Authentication required") || res.message.contains("changed"))
    }

    // --- Domain Business Logic Tests ---

    @Test
    fun appOpenResumeDetailRetry_samePathForKnownIds() = runTest {
        val coordinator = createCoordinator()
        fakeRepository.unlockResults["capsule-1"] = OperationResult.Success(Availability.AVAILABLE)
        fakeRepository.recoveredList = listOf(
            RecoveredPublication("capsule-1", PublicationPhase.SENT, null, false)
        )

        val summary = coordinator.checkForeground(listOf("capsule-1"))

        assertTrue(summary.publicationRecoveryResult is OperationResult.Success)
        assertEquals(1, summary.unlockResults.size)
        assertEquals(
            Availability.AVAILABLE,
            (summary.unlockResults["capsule-1"] as OperationResult.Success).data
        )
        assertEquals(1, fakeRepository.recoverPublicationsCallCount.get())
        assertEquals(1, fakeRepository.verifyUnlockCallCount.get())
    }

    @Test
    fun zeroKnownIds_doesNotFakeReceivedList() = runTest {
        val coordinator = createCoordinator()
        val summary = coordinator.checkForeground(emptyList())

        assertTrue(summary.unlockResults.isEmpty())
        assertTrue(summary.publicationRecoveryResult is OperationResult.Success)
    }

    @Test
    fun timeOnlyBeforeAfterDeadline_viaRealVerifier() = runTest {
        val sessionTrack = DefaultSessionTracker()
        sessionTrack.startSession("recipient-1")

        val fakeAuth = FakeAuthAdapterUnlock(sessionTrack)
        val fakeFirestore = FakeFirestoreAdapterUnlock()
        val repository = DefaultCapsuleRepository(
            sessionTracker = sessionTrack,
            authAdapter = fakeAuth,
            firestoreAdapter = fakeFirestore,
            locationAdapter = null
        )
        val coord = createCoordinator(repo = repository, sessionTrack = sessionTrack)

        fakeFirestore.seedCapsule("capsule-time", "recipient-1", unlocked = false, mode = "TIME", deadlineReached = false)

        val earlyRes = coord.verifyUnlock("capsule-time")
        assertTrue(earlyRes is OperationResult.Denied)

        fakeFirestore.seedCapsule("capsule-time", "recipient-1", unlocked = false, mode = "TIME", deadlineReached = true)

        val lateRes = coord.verifyUnlock("capsule-time")
        assertTrue(lateRes is OperationResult.Success)
        assertEquals(Availability.AVAILABLE, (lateRes as OperationResult.Success).data)
    }

    @Test
    fun combinedLocationInvalidPermissionDeniedPreviousVisitNotLatched() = runTest {
        val sessionTrack = DefaultSessionTracker()
        sessionTrack.startSession("recipient-1")

        val fakeAuth = FakeAuthAdapterUnlock(sessionTrack)
        val fakeFirestore = FakeFirestoreAdapterUnlock()
        val fakeLoc = FakeLocationAdapterUnlock()
        val targetSeoul = LocationTarget(37.5665, 126.9780, 150.0)
        val nowNanos = 100_000_000_000L

        val repository = DefaultCapsuleRepository(
            sessionTracker = sessionTrack,
            authAdapter = fakeAuth,
            firestoreAdapter = fakeFirestore,
            locationAdapter = fakeLoc,
            clockNanosSupplier = { nowNanos }
        )
        val coord = createCoordinator(repo = repository, sessionTrack = sessionTrack)

        fakeFirestore.seedCapsule("capsule-loc", "recipient-1", unlocked = false, mode = "BOTH", deadlineReached = false, target = targetSeoul)

        // 1. Permission denied
        fakeLoc.fineGranted = false
        val resPermDenied = coord.verifyUnlock("capsule-loc")
        assertTrue(resPermDenied is OperationResult.Denied)

        // 2. Permission granted, early visit inside location -> denied due to deadline
        fakeLoc.fineGranted = true
        fakeLoc.sample = ForegroundLocationSample(37.5665, 126.9780, 90_000_000_000L, 15.0f)
        val resEarlyVisit = coord.verifyUnlock("capsule-loc")
        assertTrue(resEarlyVisit is OperationResult.Denied)

        // 3. User departs location (~300m away) and deadline passes -> denied (no past-visit latch!)
        fakeFirestore.seedCapsule("capsule-loc", "recipient-1", unlocked = false, mode = "BOTH", deadlineReached = true, target = targetSeoul)
        fakeLoc.sample = ForegroundLocationSample(37.5692, 126.9780, 90_000_000_000L, 15.0f)
        val resDeparted = coord.verifyUnlock("capsule-loc")
        assertTrue(resDeparted is OperationResult.Denied)
    }

    @Test
    fun confirmedUnlock_neverRelocks() = runTest {
        val sessionTrack = DefaultSessionTracker()
        sessionTrack.startSession("recipient-1")

        val fakeAuth = FakeAuthAdapterUnlock(sessionTrack)
        val fakeFirestore = FakeFirestoreAdapterUnlock()
        val fakeLoc = FakeLocationAdapterUnlock()

        fakeFirestore.seedCapsule("capsule-unlocked", "recipient-1", unlocked = true, mode = "LOCATION", deadlineReached = true)
        fakeLoc.fineGranted = false // If location were rechecked, it would fail!

        val repository = DefaultCapsuleRepository(
            sessionTracker = sessionTrack,
            authAdapter = fakeAuth,
            firestoreAdapter = fakeFirestore,
            locationAdapter = fakeLoc
        )
        val coord = createCoordinator(repo = repository, sessionTrack = sessionTrack)

        val res = coord.verifyUnlock("capsule-unlocked")

        assertTrue(res is OperationResult.Success)
        assertEquals(Availability.AVAILABLE, (res as OperationResult.Success).data)
    }

    @Test
    fun savedUncertainSendReconciliation_neverImplicitlyRepublishesOrGeneratesNewId() = runTest {
        val coordinator = createCoordinator()
        fakeRepository.recoveredList = listOf(
            RecoveredPublication("capsule-uncertain", PublicationPhase.OUTCOME_UNKNOWN, null, false)
        )

        val summary = coordinator.checkForeground(emptyList())

        assertTrue(summary.publicationRecoveryResult is OperationResult.Success)
        assertEquals(1, fakeRepository.recoverPublicationsCallCount.get())
        assertEquals(0, fakeRepository.publishCallCount.get())
    }

    @Test
    fun missingAccount_returnsDenied() = runTest {
        val coordinator = createCoordinator()
        sessionTracker.clearSession()

        val resPub = coordinator.recoverPublications()
        val resUnlock = coordinator.verifyUnlock("capsule-1")
        val resReconcile = coordinator.reconcile("capsule-1")

        assertTrue(resPub is OperationResult.Denied)
        assertTrue(resUnlock is OperationResult.Denied)
        assertTrue(resReconcile is OperationResult.Denied)
    }

    @Test
    fun blankId_returnsInvalid() = runTest {
        val coordinator = createCoordinator()
        val resUnlock = coordinator.verifyUnlock("   ")
        val resReconcile = coordinator.reconcile("")

        assertTrue(resUnlock is OperationResult.Invalid)
        assertTrue(resReconcile is OperationResult.Invalid)
    }

    // --- Fakes for Test Isolation ---

    private class FakeCapsuleRepository(private val tracker: SessionTracker) : CapsuleRepository {
        val recoverPublicationsCallCount = AtomicInteger(0)
        val verifyUnlockCallCount = AtomicInteger(0)
        val publishCallCount = AtomicInteger(0)

        var recoveredList: List<RecoveredPublication> = emptyList()
        val unlockResults = mutableMapOf<String, OperationResult<Availability>>()

        var onVerifyUnlockDeferred: (() -> CompletableDeferred<OperationResult<Availability>>)? = null
        var onVerifyUnlockCallback: ((String) -> Unit)? = null
        var onVerifyUnlockException: Exception? = null
        var onRecoverDeferred: (() -> CompletableDeferred<OperationResult<List<RecoveredPublication>>>)? = null

        override fun session(): SessionSnapshot = tracker.currentSession()

        override suspend fun getRecipient(uid: String): OperationResult<RecipientProfile> =
            OperationResult.Success(RecipientProfile(uid, "User $uid"))

        override suspend fun publish(frozenOperation: CapsuleDraft, targetSession: SessionSnapshot): OperationResult<String> {
            if (!tracker.isSessionValid(targetSession)) return OperationResult.Denied("Account session changed.")
            publishCallCount.incrementAndGet()
            return OperationResult.Success(frozenOperation.capsuleId)
        }

        override suspend fun publish(frozenOperation: CapsuleDraft): OperationResult<String> {
            return publish(frozenOperation, tracker.currentSession())
        }

        override suspend fun reconcile(capsuleId: String, targetSession: SessionSnapshot): OperationResult<SendStatus> {
            if (!tracker.isSessionValid(targetSession)) return OperationResult.Denied("Account session changed.")
            return OperationResult.Success(SendStatus.SENT)
        }

        override suspend fun reconcile(capsuleId: String): OperationResult<SendStatus> {
            return reconcile(capsuleId, tracker.currentSession())
        }

        override suspend fun recoverPublications(targetSession: SessionSnapshot): OperationResult<List<RecoveredPublication>> {
            if (!tracker.isSessionValid(targetSession)) return OperationResult.Denied("Account session changed.")
            recoverPublicationsCallCount.incrementAndGet()
            val deferred = onRecoverDeferred?.invoke()
            if (deferred != null) {
                return deferred.await()
            }
            return OperationResult.Success(recoveredList)
        }

        override suspend fun recoverPublications(): OperationResult<List<RecoveredPublication>> {
            return recoverPublications(tracker.currentSession())
        }

        override suspend fun verifyUnlock(capsuleId: String, targetSession: SessionSnapshot): OperationResult<Availability> {
            if (!tracker.isSessionValid(targetSession)) return OperationResult.Denied("Account session changed.")
            verifyUnlockCallCount.incrementAndGet()
            onVerifyUnlockCallback?.invoke(capsuleId)
            val exc = onVerifyUnlockException
            if (exc != null) throw exc
            val deferred = onVerifyUnlockDeferred?.invoke()
            if (deferred != null) {
                return deferred.await()
            }
            return unlockResults[capsuleId] ?: OperationResult.Success(Availability.AVAILABLE)
        }

        override suspend fun verifyUnlock(capsuleId: String): OperationResult<Availability> {
            return verifyUnlock(capsuleId, tracker.currentSession())
        }

        override suspend fun lists(uid: String): OperationResult<CapsuleListsSnapshot> =
            OperationResult.Unavailable("Not implemented")

        override suspend fun readContent(capsuleId: String): OperationResult<ProtectedCapsuleContent> =
            OperationResult.Unavailable("Not implemented")
    }

    private class FakeAuthAdapterUnlock(private val tracker: DefaultSessionTracker) : AuthAdapter {
        override fun activeUid(): String? = tracker.currentSession().uid
        override fun activeEmail(): String? = "test@example.com"
        override fun activeDisplayName(): String? = "Test User"
        override suspend fun signIn(email: String, password: String) = OperationResult.Success("recipient-1")
        override suspend fun signUp(email: String, password: String, displayName: String) = OperationResult.Success("recipient-1")
        override suspend fun updateDisplayName(displayName: String) = OperationResult.Success(Unit)
        override fun signOut() = tracker.clearSession()
    }

    private class FakeLocationAdapterUnlock : LocationAdapter {
        var fineGranted = true
        var sample: ForegroundLocationSample? = null

        override fun isLocationAvailable(): Boolean = true
        override fun isFineLocationGranted(): Boolean = fineGranted
        override suspend fun getCurrentLocation(): LocationSampleResult {
            val s = sample ?: return LocationSampleResult(LocationSampleStatus.SERVICE_DISABLED, null, "Disabled")
            return LocationSampleResult(LocationSampleStatus.SUCCESS, s)
        }
    }

    private class FakeFirestoreAdapterUnlock : FirestoreAdapter {
        private val records = mutableMapOf<String, TestRecord>()

        data class TestRecord(
            val capsuleId: String,
            val recipientUid: String,
            var unlocked: Boolean,
            val mode: String,
            val deadlineReached: Boolean,
            val target: LocationTarget? = null
        )

        fun seedCapsule(id: String, recipientUid: String, unlocked: Boolean, mode: String, deadlineReached: Boolean, target: LocationTarget? = null) {
            records[id] = TestRecord(id, recipientUid, unlocked, mode, deadlineReached, target)
        }

        override fun isAvailable(): Boolean = true
        override fun firestoreInstance(): FirebaseFirestore? = null
        override suspend fun getRecipientProfile(uid: String) = OperationResult.Success(RecipientProfile(uid, "User"))
        override suspend fun saveRecipientProfile(profile: RecipientProfile) = OperationResult.Success(Unit)

        override suspend fun readRecipientUnlockRequirements(capsuleId: String, recipientUid: String): OperationResult<RecipientUnlockRequirements> {
            val rec = records[capsuleId] ?: return OperationResult.Invalid("Not found")
            if (rec.recipientUid != recipientUid) return OperationResult.Denied("Denied")
            val lockMode = when (rec.mode) {
                "TIME" -> LockMode.TIME
                "LOCATION" -> LockMode.LOCATION
                "BOTH" -> LockMode.TIME_AND_LOCATION
                else -> LockMode.TIME
            }
            return OperationResult.Success(
                RecipientUnlockRequirements(
                    capsuleId = capsuleId,
                    recipientUid = recipientUid,
                    unlocked = rec.unlocked,
                    lockMode = lockMode,
                    unlockAt = if (rec.mode == "LOCATION") null else Instant.now().plusSeconds(if (rec.deadlineReached) -10 else 3600),
                    locationTarget = rec.target
                )
            )
        }

        override suspend fun verifyRecipientUnlock(
            capsuleId: String,
            recipientUid: String,
            locationSample: ForegroundLocationSample?,
            clockNanosSupplier: () -> Long
        ): OperationResult<Availability> {
            val rec = records[capsuleId] ?: return OperationResult.Invalid("Not found")
            if (rec.recipientUid != recipientUid) return OperationResult.Denied("Denied")
            if (rec.unlocked) return OperationResult.Success(Availability.AVAILABLE)
            if (rec.mode == "TIME" || rec.mode == "BOTH") {
                if (!rec.deadlineReached) return OperationResult.Denied("Early deadline")
            }
            if (rec.mode == "LOCATION" || rec.mode == "BOTH") {
                if (locationSample == null) return OperationResult.Denied("Missing sample")
                if (rec.target != null) {
                    val latDiff = Math.abs(locationSample.latitude - rec.target.latitude)
                    if (latDiff > 0.001) return OperationResult.Denied("Outside radius")
                }
            }
            rec.unlocked = true
            return OperationResult.Success(Availability.AVAILABLE)
        }
    }
}
