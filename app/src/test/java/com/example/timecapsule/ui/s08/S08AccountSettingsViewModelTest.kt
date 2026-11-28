package com.example.timecapsule.ui.s08

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.example.timecapsule.data.AccountSessionManager
import com.example.timecapsule.data.adapter.AuthAdapter
import com.example.timecapsule.data.adapter.FirestoreAdapter
import com.example.timecapsule.data.session.DefaultSessionTracker
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeAuthAdapterForS08 : AuthAdapter {
    var uid: String? = "test-uid-123"
    var name: String? = "Initial Name"
    var updateCount = 0
    var signOutCount = 0

    override fun activeUid(): String? = uid
    override fun activeEmail(): String? = uid?.let { "$it@example.com" }
    override fun activeDisplayName(): String? = name

    override suspend fun signIn(email: String, password: String): OperationResult<String> {
        uid = "test-uid-123"
        return OperationResult.Success(uid!!)
    }

    override suspend fun signUp(email: String, password: String, displayName: String): OperationResult<String> {
        uid = "test-uid-123"
        name = displayName
        return OperationResult.Success(uid!!)
    }

    override suspend fun updateDisplayName(displayName: String): OperationResult<Unit> {
        updateCount++
        name = displayName
        return OperationResult.Success(Unit)
    }

    override fun signOut() {
        signOutCount++
        uid = null
        name = null
    }
}

private class FakeFirestoreAdapterForS08 : FirestoreAdapter {
    var profileToReturn: RecipientProfile? = RecipientProfile("test-uid-123", "Remote Old Profile Name")
    var getProfileDeferred: CompletableDeferred<Unit>? = null

    override fun isAvailable(): Boolean = true
    override fun firestoreInstance(): FirebaseFirestore? = null

    override suspend fun getRecipientProfile(uid: String): OperationResult<RecipientProfile> {
        getProfileDeferred?.await()
        val profile = profileToReturn
        return if (profile != null && profile.uid == uid) {
            OperationResult.Success(profile)
        } else {
            OperationResult.Invalid("Profile not found")
        }
    }

    override suspend fun saveRecipientProfile(profile: RecipientProfile): OperationResult<Unit> {
        profileToReturn = profile
        return OperationResult.Success(Unit)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class S08AccountSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var authAdapter: FakeAuthAdapterForS08
    private lateinit var firestoreAdapter: FakeFirestoreAdapterForS08
    private lateinit var sessionTracker: DefaultSessionTracker
    private lateinit var accountSessionManager: AccountSessionManager
    private lateinit var viewModel: S08AccountSettingsViewModel

    @Before
    fun setUp() {
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) { runnable.run() }
            override fun postToMainThread(runnable: Runnable) { runnable.run() }
            override fun isMainThread(): Boolean = true
        })
        Dispatchers.setMain(testDispatcher)
        authAdapter = FakeAuthAdapterForS08()
        firestoreAdapter = FakeFirestoreAdapterForS08()
        sessionTracker = DefaultSessionTracker()
        accountSessionManager = AccountSessionManager(authAdapter, firestoreAdapter, sessionTracker, storageDispatcher = testDispatcher)
        runTest(testDispatcher) { accountSessionManager.restoreSessionIfAuthenticated() }

        viewModel = S08AccountSettingsViewModel(accountSessionManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ArchTaskExecutor.getInstance().setDelegate(null)
    }

    @Test
    fun checkProfilePostsRemoteProfileResult() = runBlockingTest {
        viewModel.checkProfile("test-uid-123")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.profileCheckState.value
        assertTrue(state is OperationResult.Success)
        assertEquals("Remote Old Profile Name", (state as OperationResult.Success).data.displayName)
    }

    @Test
    fun repairProfileStoresLastSubmittedNameAndPostsSuccess() = runBlockingTest {
        viewModel.repairProfile("New Submitted Name")
        assertEquals("New Submitted Name", viewModel.lastSubmittedName)

        testDispatcher.scheduler.advanceUntilIdle()

        val updateResult = viewModel.updateState.value
        assertTrue(updateResult is OperationResult.Success)
        assertEquals("New Submitted Name", authAdapter.activeDisplayName())
    }

    @Test
    fun repairProfilePreventsDuplicateConcurrentSubmissions() = runBlockingTest {
        viewModel.repairProfile("First Name")
        viewModel.repairProfile("Second Name")

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("First Name", viewModel.lastSubmittedName)
        assertEquals(1, authAdapter.updateCount)
    }

    @Test
    fun delayedCheckProfileCompletingAfterNewerUpdateIsIgnored() = runBlockingTest {
        val deferred = CompletableDeferred<Unit>()
        firestoreAdapter.getProfileDeferred = deferred

        // Launch delayed checkProfile (token 1)
        viewModel.checkProfile("test-uid-123")

        // Perform a newer profile update (token 2)
        viewModel.repairProfile("Newer Updated Name")
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify update completed successfully
        assertEquals("Newer Updated Name", viewModel.lastSubmittedName)

        // Now resume the old delayed checkProfile (token 1)
        deferred.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        // The stale checkProfile result must NOT overwrite profileCheckState
        assertNull(viewModel.profileCheckState.value)
    }

    @Test
    fun repairProfilePreservesLastSubmittedNameIndependentlyOfViewTextChanges() = runBlockingTest {
        viewModel.repairProfile("Name Saved To Cloud")

        assertEquals("Name Saved To Cloud", viewModel.lastSubmittedName)

        testDispatcher.scheduler.advanceUntilIdle()

        val updateResult = viewModel.updateState.value
        assertTrue(updateResult is OperationResult.Success)
        assertEquals("Name Saved To Cloud", viewModel.lastSubmittedName)
    }

    @Test
    fun clearStateResetsUpdateState() = runBlockingTest {
        viewModel.repairProfile("Updated Name")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.clearState()
        assertNull(viewModel.updateState.value)
    }

    @Test
    fun signOutClearsSessionInManager() = runBlockingTest {
        viewModel.signOut()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(authAdapter.activeUid())
    }

    @Test
    fun repairProfile_obsoleteSessionSameUidDifferentGeneration_isDenied() = runBlockingTest {
        val obsoleteSession = SessionSnapshot("test-uid-123", 99L) // Current generation is 2L
        viewModel.repairProfile("Obsolete Name", obsoleteSession)
        testDispatcher.scheduler.advanceUntilIdle()

        val updateResult = viewModel.updateState.value
        assertTrue(updateResult is OperationResult.Denied)
        assertEquals("Initial Name", authAdapter.activeDisplayName())
    }

    @Test
    fun repairProfile_differentUidSession_isDenied() = runBlockingTest {
        val wrongUserSession = SessionSnapshot("other-user-456", 2L)
        viewModel.repairProfile("Wrong User Name", wrongUserSession)
        testDispatcher.scheduler.advanceUntilIdle()

        val updateResult = viewModel.updateState.value
        assertTrue(updateResult is OperationResult.Denied)
        assertEquals("Initial Name", authAdapter.activeDisplayName())
    }

    @Test
    fun repairProfile_sessionChangeDuringAsyncOperation_isDenied() = runBlockingTest {
        val currentSession = accountSessionManager.currentSession()

        // Start repair profile with current session
        viewModel.repairProfile("Name In Flight", currentSession)

        // Mid-flight session generation change (e.g. relogin / session reset)
        sessionTracker.startSession("test-uid-123") // Increments generation to 3L
        testDispatcher.scheduler.advanceUntilIdle()

        val updateResult = viewModel.updateState.value
        assertTrue(updateResult is OperationResult.Denied)
    }

    @Test
    fun signOut_obsoleteSession_isDenied() = runBlockingTest {
        val obsoleteSession = SessionSnapshot("test-uid-123", 99L)
        viewModel.signOut(obsoleteSession)
        testDispatcher.scheduler.advanceUntilIdle()

        val signOutResult = viewModel.signOutState.value
        assertTrue(signOutResult is OperationResult.Denied)
        assertEquals("test-uid-123", authAdapter.activeUid())
    }

    @Test
    fun signOut_preventsDuplicateConcurrentInFlightSubmissions() = runBlockingTest {
        val currentSession = accountSessionManager.currentSession()
        viewModel.signOut(currentSession)
        viewModel.signOut(currentSession)

        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(authAdapter.activeUid())
        assertEquals(1, authAdapter.signOutCount)
    }

    private fun runBlockingTest(block: suspend () -> Unit) = runBlocking { block() }
}
