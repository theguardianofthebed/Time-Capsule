package com.example.timecapsule.ui.s03

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
import com.example.timecapsule.domain.model.RecoveredPublication
import com.example.timecapsule.domain.model.SendStatus
import com.example.timecapsule.domain.model.UnlockVerificationOutcome
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
class S03CapsuleListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var sessionTracker: DefaultSessionTracker
    private lateinit var fakeRepository: FakeCapsuleRepository
    private lateinit var viewModel: S03CapsuleListViewModel

    private val userA = "user-a"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        sessionTracker = DefaultSessionTracker()
        sessionTracker.startSession(userA)
        fakeRepository = FakeCapsuleRepository(sessionTracker)
        viewModel = S03CapsuleListViewModel(sessionTracker, fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadLists loads sent and received lists for active user`() = runTest(testDispatcher) {
        val now = Instant.now()
        val capSent = CapsuleSummary("c1", userA, "user-b", CapsuleLock(LockMode.TIME), SendStatus.SENT, title = "Sent Cap", createdAt = now)
        val capRecv = CapsuleSummary("c2", "user-b", userA, CapsuleLock(LockMode.TIME), SendStatus.SENT, title = "Recv Cap", createdAt = now)
        fakeRepository.listsResult = OperationResult.Success(CapsuleListsSnapshot(sent = listOf(capSent), received = listOf(capRecv)))

        viewModel.loadLists()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.sentList.size)
        assertEquals(1, state.receivedList.size)
        assertEquals("Sent Cap", state.sentList.first().title)
        assertEquals("Recv Cap", state.receivedList.first().title)
    }

    @Test
    fun `selectTab updates selected tab in state`() = runTest(testDispatcher) {
        assertEquals(CapsuleTab.RECEIVED, viewModel.uiState.value.selectedTab)
        viewModel.selectTab(CapsuleTab.SENT)
        assertEquals(CapsuleTab.SENT, viewModel.uiState.value.selectedTab)
    }

    private class FakeCapsuleRepository(
        private val sessionTracker: DefaultSessionTracker
    ) : CapsuleRepository {
        var listsResult: OperationResult<CapsuleListsSnapshot> = OperationResult.Success(CapsuleListsSnapshot(emptyList(), emptyList()))

        override fun session(): SessionSnapshot = sessionTracker.currentSession()
        override suspend fun getRecipient(uid: String): OperationResult<RecipientProfile> = OperationResult.Invalid("")
        override suspend fun publish(frozenOperation: CapsuleDraft): OperationResult<String> = OperationResult.Invalid("")
        override suspend fun reconcile(capsuleId: String): OperationResult<SendStatus> = OperationResult.Invalid("")
        override suspend fun lists(uid: String): OperationResult<CapsuleListsSnapshot> = listsResult
        override suspend fun lists(uid: String, targetSession: SessionSnapshot): OperationResult<CapsuleListsSnapshot> = listsResult
        override suspend fun readContent(capsuleId: String): OperationResult<ProtectedCapsuleContent> = OperationResult.Invalid("")
    }
}
