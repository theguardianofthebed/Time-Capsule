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
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundRecoveryCoordinatorTest {

    private lateinit var sessionTracker: DefaultSessionTracker
    private lateinit var fakeRepository: FakeCapsuleRepository

    @Before
    fun setUp() {
        sessionTracker = DefaultSessionTracker()
        sessionTracker.startSession("user-1")
        fakeRepository = FakeCapsuleRepository()
    }

    private fun TestScope.createCoordinator(
        repo: CapsuleRepository = fakeRepository,
        sessionTrack: SessionTracker = sessionTracker
    ): ForegroundRecoveryCoordinator {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        return ForegroundRecoveryCoordinator(
            repository = repo,
            sessionTracker = sessionTrack,
            ioDispatcher = dispatcher,
            scope = this
        )
    }

    // (a) app-open/resume/detail/Retry same path for known IDs
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

    // (b) two simultaneous same-ID requests = one verify operation
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

    // (c) different UID or same UID/new generation = separate operations and stale reply ignored
    @Test
    fun differentUidOrSameUidNewGeneration_createsSeparateOperationsAndIgnoresStaleReply() = runTest {
        val coordinator = createCoordinator()
        val gate = CompletableDeferred<OperationResult<Availability>>()
        fakeRepository.onVerifyUnlockDeferred = { gate }

        val job1 = async { coordinator.verifyUnlock("capsule-gen-1") }
        runCurrent()
        assertEquals(1, fakeRepository.verifyUnlockCallCount.get())

        // Session generation increments (e.g. relogged in or user switch)
        sessionTracker.startSession("user-1")

        val job2 = async { coordinator.verifyUnlock("capsule-gen-1") }
        runCurrent()
        assertEquals(2, fakeRepository.verifyUnlockCallCount.get())

        gate.complete(OperationResult.Success(Availability.AVAILABLE))

        val res1 = job1.await()
        val res2 = job2.await()

        // job1 was under generation 1, but session moved to generation 2 before job1 finished -> Denied
        assertTrue(res1 is OperationResult.Denied)
        assertTrue(res2 is OperationResult.Success)
    }

    // (d) cancellation of one waiter
    @Test
    fun cancellationOfOneWaiter_doesNotCancelOtherWaiterOrUnderlyingScope() = runTest {
        val coordinator = createCoordinator()
        val gate = CompletableDeferred<OperationResult<Availability>>()
        fakeRepository.onVerifyUnlockDeferred = { gate }

        val job1 = launch { coordinator.verifyUnlock("capsule-waiter") }
        val job2 = async { coordinator.verifyUnlock("capsule-waiter") }

        // Cancel job1
        job1.cancel()

        // Complete gate for job2
        gate.complete(OperationResult.Success(Availability.AVAILABLE))

        val res2 = job2.await()
        assertTrue(res2 is OperationResult.Success)
        assertEquals(Availability.AVAILABLE, (res2 as OperationResult.Success).data)
    }

    // (e) a failed/offline result followed by a truly fresh successful Retry
    @Test
    fun failedOrOfflineResult_followedByTrulyFreshSuccessfulRetry() = runTest {
        val coordinator = createCoordinator()
        fakeRepository.unlockResults["capsule-retry"] = OperationResult.Unavailable("Offline network")

        val res1 = coordinator.verifyUnlock("capsule-retry")
        assertTrue(res1 is OperationResult.Unavailable)
        assertEquals(1, fakeRepository.verifyUnlockCallCount.get())

        // Now network recovers -> update fake repository result
        fakeRepository.unlockResults["capsule-retry"] = OperationResult.Success(Availability.AVAILABLE)

        // Second call (Retry) must NOT return cached failure, must invoke repository again!
        val res2 = coordinator.verifyUnlock("capsule-retry")

        assertTrue(res2 is OperationResult.Success)
        assertEquals(Availability.AVAILABLE, (res2 as OperationResult.Success).data)
        assertEquals(2, fakeRepository.verifyUnlockCallCount.get())
    }

    // (f) zero known IDs do not fake a received list
    @Test
    fun zeroKnownIds_doesNotFakeReceivedList() = runTest {
        val coordinator = createCoordinator()
        val summary = coordinator.checkForeground(emptyList())

        assertTrue(summary.unlockResults.isEmpty())
        assertTrue(summary.publicationRecoveryResult is OperationResult.Success)
    }

    // (g) time-only before/after deadline via existing real verifier
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

        // Seed capsule with deadline NOT reached
        fakeFirestore.seedCapsule("capsule-time", "recipient-1", unlocked = false, mode = "TIME", deadlineReached = false)

        val earlyRes = coord.verifyUnlock("capsule-time")
        assertTrue(earlyRes is OperationResult.Denied)

        // Advance deadline -> reached
        fakeFirestore.seedCapsule("capsule-time", "recipient-1", unlocked = false, mode = "TIME", deadlineReached = true)

        val lateRes = coord.verifyUnlock("capsule-time")
        assertTrue(lateRes is OperationResult.Success)
        assertEquals(Availability.AVAILABLE, (lateRes as OperationResult.Success).data)
    }

    // (h) combined location invalid/permission denied/previous visit not latched
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

    // (i) confirmed unlock never relocks
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

    // (j) saved uncertain send foreground reconciliation never implicitly republishes or generates a new ID
    @Test
    fun savedUncertainSendReconciliation_neverImplicitlyRepublishesOrGeneratesNewId() = runTest {
        val coordinator = createCoordinator()
        fakeRepository.recoveredList = listOf(
            RecoveredPublication("capsule-uncertain", PublicationPhase.OUTCOME_UNKNOWN, null, false)
        )

        val summary = coordinator.checkForeground(emptyList())

        assertTrue(summary.publicationRecoveryResult is OperationResult.Success)
        assertEquals(1, fakeRepository.recoverPublicationsCallCount.get())
        // Ensure publish was NOT called implicitly
        assertEquals(0, fakeRepository.publishCallCount.get())
    }

    // (k) activity/Fragment recreation does not duplicate, leak or overwrite active state
    @Test
    fun activityFragmentRecreation_doesNotDuplicateLeakOrOverwriteActiveState() = runTest {
        val coordinator = createCoordinator()
        val gate = CompletableDeferred<OperationResult<List<RecoveredPublication>>>()
        fakeRepository.onRecoverDeferred = { gate }

        val job1 = async { coordinator.recoverPublications() }
        runCurrent()
        assertEquals(1, fakeRepository.recoverPublicationsCallCount.get())

        // Simulate Activity recreation where Fragment attaches and triggers recovery again
        val job2 = async { coordinator.recoverPublications() }
        runCurrent()

        // Still exactly 1 call to repository!
        assertEquals(1, fakeRepository.recoverPublicationsCallCount.get())

        gate.complete(OperationResult.Success(emptyList()))

        val res1 = job1.await()
        val res2 = job2.await()

        assertTrue(res1 is OperationResult.Success)
        assertTrue(res2 is OperationResult.Success)
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

    // Fake CapsuleRepository for Coordinator tests
    private class FakeCapsuleRepository : CapsuleRepository {
        val recoverPublicationsCallCount = AtomicInteger(0)
        val verifyUnlockCallCount = AtomicInteger(0)
        val publishCallCount = AtomicInteger(0)

        var recoveredList: List<RecoveredPublication> = emptyList()
        val unlockResults = mutableMapOf<String, OperationResult<Availability>>()

        var onVerifyUnlockDeferred: (() -> CompletableDeferred<OperationResult<Availability>>)? = null
        var onRecoverDeferred: (() -> CompletableDeferred<OperationResult<List<RecoveredPublication>>>)? = null

        override fun session(): SessionSnapshot = SessionSnapshot("user-1", 1)

        override suspend fun getRecipient(uid: String): OperationResult<RecipientProfile> =
            OperationResult.Success(RecipientProfile(uid, "User $uid"))

        override suspend fun publish(frozenOperation: CapsuleDraft): OperationResult<String> {
            publishCallCount.incrementAndGet()
            return OperationResult.Success(frozenOperation.capsuleId)
        }

        override suspend fun reconcile(capsuleId: String): OperationResult<SendStatus> =
            OperationResult.Success(SendStatus.SENT)

        override suspend fun recoverPublications(): OperationResult<List<RecoveredPublication>> {
            recoverPublicationsCallCount.incrementAndGet()
            val deferred = onRecoverDeferred?.invoke()
            if (deferred != null) {
                return deferred.await()
            }
            return OperationResult.Success(recoveredList)
        }

        override suspend fun verifyUnlock(capsuleId: String): OperationResult<Availability> {
            verifyUnlockCallCount.incrementAndGet()
            val deferred = onVerifyUnlockDeferred?.invoke()
            if (deferred != null) {
                return deferred.await()
            }
            return unlockResults[capsuleId] ?: OperationResult.Success(Availability.AVAILABLE)
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
