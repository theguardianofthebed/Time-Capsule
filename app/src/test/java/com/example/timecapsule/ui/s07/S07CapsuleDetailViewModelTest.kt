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

@OptIn(ExperimentalCoroutinesApi::class)
class S07CapsuleDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var sessionTracker: DefaultSessionTracker
    private lateinit var fakeRepository: FakeCapsuleRepository
    private lateinit var viewModel: S07CapsuleDetailViewModel

    private val userA = "user-a"
    private val userB = "user-b"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        sessionTracker = DefaultSessionTracker()
        sessionTracker.startSession(userA)
        fakeRepository = FakeCapsuleRepository(sessionTracker)
        viewModel = S07CapsuleDetailViewModel(sessionTracker, fakeRepository)
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

        viewModel.loadDetail("cap-2")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(ParticipantRole.RECIPIENT, state.role)
        assertFalse(state.isUnlocked)
        assertEquals("Locked by time", state.unlockReason)
    }

    @Test
    fun `timeOnlyLock_accountDisabledError_mapsToAccessDenied_notLocationDisabled`() = runTest(testDispatcher) {
        sessionTracker.startSession(userB)
        val now = Instant.now()
        val cap = CapsuleSummary(
            capsuleId = "cap-3",
            senderUid = userA,
            recipientUid = userB,
            lock = CapsuleLock(LockMode.TIME, unlockAt = now.plusSeconds(3600)),
            sendStatus = SendStatus.SENT,
            availability = Availability.LOCKED,
            title = "A's Note To B"
        )
        fakeRepository.summaries["cap-3"] = cap

        viewModel.loadDetail("cap-3")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(DetailStatus.LOCKED_WAITING_TIME, state.status)
        assertFalse(state.status == DetailStatus.LOCKED_LOCATION_DISABLED)
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
