package com.example.timecapsule.ui.s07

import com.example.timecapsule.data.CapsuleRepository
import com.example.timecapsule.data.session.DefaultSessionTracker
import com.example.timecapsule.domain.model.Availability
import com.example.timecapsule.domain.model.CapsuleDraft
import com.example.timecapsule.domain.model.CapsuleLock
import com.example.timecapsule.domain.model.CapsuleListsSnapshot
import com.example.timecapsule.domain.model.CapsuleSummary
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.domain.model.ProtectedCapsuleContent
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.model.SendStatus
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

import com.example.timecapsule.data.recovery.ForegroundRecoveryCoordinator
import com.example.timecapsule.domain.model.LocationTarget

@OptIn(ExperimentalCoroutinesApi::class)
class S07CapsuleDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var sessionTracker: DefaultSessionTracker
    private lateinit var fakeRepository: FakeCapsuleRepository
    private lateinit var fakeRecoveryCoordinator: FakeRecoveryCoordinator
    private lateinit var viewModel: S07CapsuleDetailViewModel

    private val userA = "user-a"
    private val userB = "user-b"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        sessionTracker = DefaultSessionTracker()
        sessionTracker.startSession(userA)
        fakeRepository = FakeCapsuleRepository(sessionTracker)
        fakeRecoveryCoordinator = FakeRecoveryCoordinator(sessionTracker, fakeRepository)
        viewModel = S07CapsuleDetailViewModel(sessionTracker, fakeRepository, fakeRecoveryCoordinator)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadDetail for Author Copy loads protected content directly`() = runTest(testDispatcher) {
        val now = Instant.now()
        val cap = CapsuleSummary(
            capsuleId = "cap-1",
            senderUid = userA,
            recipientUid = userB,
            lock = CapsuleLock(LockMode.TIME, unlockAt = now.plusSeconds(3600)),
            sendStatus = SendStatus.SENT,
            availability = Availability.LOCKED,
            title = "My Note To B"
        )
        fakeRepository.summaries["cap-1"] = cap
        fakeRepository.content["cap-1"] = ProtectedCapsuleContent("cap-1", "A's copy of text", null)

        viewModel.loadDetail("cap-1")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(ParticipantRole.AUTHOR_COPY, state.role)
        assertEquals("A's copy of text", state.content?.text)
        assertEquals("Sender Copy", state.unlockReason)
    }

    @Test
    fun `loadDetail for Recipient when locked shows locked reason without reading content`() = runTest(testDispatcher) {
        sessionTracker.startSession(userB)
        val now = Instant.now()
        val cap = CapsuleSummary(
            capsuleId = "cap-2",
            senderUid = userA,
            recipientUid = userB,
            lock = CapsuleLock(LockMode.TIME, unlockAt = now.plusSeconds(3600)),
            sendStatus = SendStatus.SENT,
            availability = Availability.LOCKED,
            title = "A's Note To B"
        )
        fakeRepository.summaries["cap-2"] = cap
        fakeRecoveryCoordinator.verifyUnlockResult = OperationResult.Denied("Unlock authorization denied or unlock time not reached.")

        viewModel.loadDetail("cap-2")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(ParticipantRole.RECIPIENT, state.role)
        assertFalse(state.isUnlocked)
        assertEquals(DetailStatus.LOCKED_WAITING_TIME, state.status)
    }

    @Test
    fun `locationLock_accountDisabledError_mapsToAccessDenied_notLocationDisabled`() = runTest(testDispatcher) {
        sessionTracker.startSession(userB)
        val cap = CapsuleSummary(
            capsuleId = "cap-3",
            senderUid = userA,
            recipientUid = userB,
            lock = CapsuleLock(LockMode.LOCATION, location = LocationTarget(37.5, 127.0)),
            sendStatus = SendStatus.SENT,
            availability = Availability.LOCKED
        )
        fakeRepository.summaries["cap-3"] = cap
        fakeRecoveryCoordinator.verifyUnlockResult = OperationResult.Denied("User account disabled or not found.")

        viewModel.loadDetail("cap-3")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DetailStatus.ACCESS_DENIED, state.status)
    }

    @Test
    fun `bothLock_staleSessionError_mapsToAccessDenied`() = runTest(testDispatcher) {
        sessionTracker.startSession(userB)
        val now = Instant.now()
        val cap = CapsuleSummary(
            capsuleId = "cap-both-stale",
            senderUid = userA,
            recipientUid = userB,
            lock = CapsuleLock(LockMode.TIME_AND_LOCATION, unlockAt = now.plusSeconds(3600), location = LocationTarget(37.5, 127.0)),
            sendStatus = SendStatus.SENT,
            availability = Availability.LOCKED
        )
        fakeRepository.summaries["cap-both-stale"] = cap
        fakeRecoveryCoordinator.verifyUnlockResult = OperationResult.Denied("Session changed during authentication.")

        viewModel.loadDetail("cap-both-stale")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DetailStatus.ACCESS_DENIED, state.status)
    }

    @Test
    fun `locationLock_nullFixError_mapsToWaitingLocation`() = runTest(testDispatcher) {
        sessionTracker.startSession(userB)
        val cap = CapsuleSummary(
            capsuleId = "cap-null-fix",
            senderUid = userA,
            recipientUid = userB,
            lock = CapsuleLock(LockMode.LOCATION, location = LocationTarget(37.5, 127.0)),
            sendStatus = SendStatus.SENT,
            availability = Availability.LOCKED
        )
        fakeRepository.summaries["cap-null-fix"] = cap
        fakeRecoveryCoordinator.verifyUnlockResult = OperationResult.Unavailable("Location provider returned a null fix.")

        viewModel.loadDetail("cap-null-fix")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DetailStatus.LOCKED_WAITING_LOCATION, state.status)
    }

    @Test
    fun `locationLock_permissionError_mapsToPermissionRequired`() = runTest(testDispatcher) {
        sessionTracker.startSession(userB)
        val cap = CapsuleSummary(
            capsuleId = "cap-4",
            senderUid = userA,
            recipientUid = userB,
            lock = CapsuleLock(LockMode.LOCATION, location = LocationTarget(37.5, 127.0)),
            sendStatus = SendStatus.SENT,
            availability = Availability.LOCKED
        )
        fakeRepository.summaries["cap-4"] = cap
        fakeRecoveryCoordinator.verifyUnlockResult = OperationResult.Denied("Location permission denied.")

        viewModel.loadDetail("cap-4")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DetailStatus.LOCKED_PERMISSION_REQUIRED, state.status)
    }

    @Test
    fun `locationLock_disabledError_mapsToLocationDisabled`() = runTest(testDispatcher) {
        sessionTracker.startSession(userB)
        val cap = CapsuleSummary(
            capsuleId = "cap-5",
            senderUid = userA,
            recipientUid = userB,
            lock = CapsuleLock(LockMode.LOCATION, location = LocationTarget(37.5, 127.0)),
            sendStatus = SendStatus.SENT,
            availability = Availability.LOCKED
        )
        fakeRepository.summaries["cap-5"] = cap
        fakeRecoveryCoordinator.verifyUnlockResult = OperationResult.Unavailable("Location services disabled in device settings.")

        viewModel.loadDetail("cap-5")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DetailStatus.LOCKED_LOCATION_DISABLED, state.status)
    }

    @Test
    fun `timeLock_ambiguousAuthDenial_mapsToAccessDenied`() = runTest(testDispatcher) {
        sessionTracker.startSession(userB)
        val now = Instant.now()
        val cap = CapsuleSummary(
            capsuleId = "cap-time-denied",
            senderUid = userA,
            recipientUid = userB,
            lock = CapsuleLock(LockMode.TIME, unlockAt = now.plusSeconds(3600)),
            sendStatus = SendStatus.SENT,
            availability = Availability.LOCKED
        )
        fakeRepository.summaries["cap-time-denied"] = cap
        fakeRecoveryCoordinator.verifyUnlockResult = OperationResult.Denied("Unlock authorization denied.")

        viewModel.loadDetail("cap-time-denied")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DetailStatus.ACCESS_DENIED, state.status)
    }

    @Test
    fun `bothLock_unavailableNetwork_mapsToVerificationFailed`() = runTest(testDispatcher) {
        sessionTracker.startSession(userB)
        val now = Instant.now()
        val cap = CapsuleSummary(
            capsuleId = "cap-both-offline",
            senderUid = userA,
            recipientUid = userB,
            lock = CapsuleLock(LockMode.TIME_AND_LOCATION, unlockAt = now.plusSeconds(3600), location = LocationTarget(37.5, 127.0)),
            sendStatus = SendStatus.SENT,
            availability = Availability.LOCKED
        )
        fakeRepository.summaries["cap-both-offline"] = cap
        fakeRecoveryCoordinator.verifyUnlockResult = OperationResult.Unavailable("Network connection lost.")

        viewModel.loadDetail("cap-both-offline")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DetailStatus.LOCKED_VERIFICATION_FAILED, state.status)
    }

    private class FakeRecoveryCoordinator(
        sessionTracker: DefaultSessionTracker,
        repository: CapsuleRepository
    ) : ForegroundRecoveryCoordinator(repository, sessionTracker) {
        var verifyUnlockResult: OperationResult<Availability> = OperationResult.Denied("Unlock time not reached.")

        override suspend fun verifyUnlock(capsuleId: String, targetSession: SessionSnapshot): OperationResult<Availability> {
            return verifyUnlockResult
        }
    }

    private class FakeCapsuleRepository(
        private val sessionTracker: DefaultSessionTracker
    ) : CapsuleRepository {
        val summaries = mutableMapOf<String, CapsuleSummary>()
        val content = mutableMapOf<String, ProtectedCapsuleContent>()

        override fun session(): SessionSnapshot = sessionTracker.currentSession()
        override suspend fun getRecipient(uid: String): OperationResult<RecipientProfile> = OperationResult.Invalid("")
        override suspend fun publish(frozenOperation: CapsuleDraft): OperationResult<String> = OperationResult.Invalid("")
        override suspend fun reconcile(capsuleId: String): OperationResult<SendStatus> = OperationResult.Invalid("")
        override suspend fun lists(uid: String): OperationResult<CapsuleListsSnapshot> = OperationResult.Invalid("")

        override suspend fun getSummary(capsuleId: String, targetSession: SessionSnapshot): OperationResult<CapsuleSummary> {
            val sum = summaries[capsuleId] ?: return OperationResult.Invalid("Not found")
            return OperationResult.Success(sum)
        }

        override suspend fun readContent(capsuleId: String, targetSession: SessionSnapshot): OperationResult<ProtectedCapsuleContent> {
            val c = content[capsuleId] ?: return OperationResult.Invalid("Content not found")
            return OperationResult.Success(c)
        }

        override suspend fun readContent(capsuleId: String): OperationResult<ProtectedCapsuleContent> {
            val c = content[capsuleId] ?: return OperationResult.Invalid("Content not found")
            return OperationResult.Success(c)
        }
    }
}
