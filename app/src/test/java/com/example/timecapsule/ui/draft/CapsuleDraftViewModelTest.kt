package com.example.timecapsule.ui.draft

import com.example.timecapsule.data.AccountSessionManager
import com.example.timecapsule.data.adapter.AuthAdapter
import com.example.timecapsule.data.adapter.FirestoreAdapter
import com.example.timecapsule.data.session.DefaultSessionTracker
import com.example.timecapsule.domain.model.LocationTarget
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.result.OperationResult
import com.google.firebase.firestore.FirebaseFirestore
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CapsuleDraftViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var sessionTracker: DefaultSessionTracker
    private lateinit var mockAuthAdapter: FakeAuthAdapter
    private lateinit var mockFirestoreAdapter: FakeFirestoreAdapter
    private lateinit var accountSessionManager: AccountSessionManager
    private lateinit var viewModel: CapsuleDraftViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        sessionTracker = DefaultSessionTracker()
        mockAuthAdapter = FakeAuthAdapter(initialUid = "sender-uid")
        mockFirestoreAdapter = FakeFirestoreAdapter()
        accountSessionManager = AccountSessionManager(mockAuthAdapter, mockFirestoreAdapter, sessionTracker)
        accountSessionManager.restoreSessionIfAuthenticated()

        viewModel = CapsuleDraftViewModel(accountSessionManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- S-04 Content Validation Tests ---

    @Test
    fun `textOnlyIsAccepted - valid message body passes validation`() {
        viewModel.updateText("A message for the future")
        val res = viewModel.validateS04Content()
        assertTrue(res is OperationResult.Success)
    }

    @Test
    fun `photoOnlyIsAccepted - attached photo without text passes validation`() {
        val attachRes = viewModel.attachPhoto("content://media/photo.jpg", "photo.jpg", 1024L)
        assertTrue(attachRes is OperationResult.Success)

        val res = viewModel.validateS04Content()
        assertTrue(res is OperationResult.Success)
    }

    @Test
    fun `textAndPhotoAccepted - both text and photo pass validation`() {
        viewModel.updateText("A message with photo")
        viewModel.attachPhoto("content://media/photo.jpg", "photo.jpg", 2048L)

        val res = viewModel.validateS04Content()
        assertTrue(res is OperationResult.Success)
    }

    @Test
    fun `titleOnlyIsRejected - title alone without message or photo is invalid`() {
        viewModel.updateTitle("Graduation Day")
        viewModel.updateText("")

        val res = viewModel.validateS04Content()
        assertTrue(res is OperationResult.Invalid)
    }

    @Test
    fun `emptyContentIsRejected - empty text and no photo is invalid`() {
        viewModel.updateText("   ")
        val res = viewModel.validateS04Content()
        assertTrue(res is OperationResult.Invalid)
    }

    @Test
    fun `oversizedPhotoRejected - photo over 5 MiB is rejected`() {
        val oversized = 5 * 1024 * 1024 + 1L
        val res = viewModel.attachPhoto("content://media/large.png", "large.png", oversized)

        assertTrue(res is OperationResult.Invalid)
        assertNull(viewModel.uiState.value.localPhotoUri)
    }

    @Test
    fun `validPhotoAccepted - photo under 5 MiB is attached successfully`() {
        val validSize = 5 * 1024 * 1024L
        val res = viewModel.attachPhoto("content://media/valid.jpg", "valid.jpg", validSize)

        assertTrue(res is OperationResult.Success)
        assertEquals("content://media/valid.jpg", viewModel.uiState.value.localPhotoUri)
        assertEquals("valid.jpg", viewModel.uiState.value.photoName)
    }

    // --- S-05 Recipient Lookup & Explicit Confirmation Tests ---

    @Test
    fun `selectFutureMe - automatically resolves and confirms self recipient`() {
        viewModel.selectFutureMe("sender-uid")

        val state = viewModel.uiState.value
        assertEquals(RecipientMode.FUTURE_ME, state.recipientMode)
        assertEquals("sender-uid", state.recipientUidInput)
        assertTrue(state.isRecipientConfirmed)
        assertNotNull(state.resolvedProfile)
        assertEquals("sender-uid", state.resolvedProfile?.uid)
    }

    @Test
    fun `updateRecipientUidInput - editing UID resets recipient confirmation`() {
        viewModel.selectFutureMe("sender-uid")
        assertTrue(viewModel.uiState.value.isRecipientConfirmed)

        viewModel.updateRecipientUidInput("sender-uid-edited")

        val state = viewModel.uiState.value
        assertFalse(state.isRecipientConfirmed)
        assertNull(state.resolvedProfile)
        assertEquals(RecipientLookupStatus.IDLE, state.lookupStatus)
    }

    @Test
    fun `performRecipientLookup - found recipient requires explicit user confirmation`() {
        mockFirestoreAdapter.profiles["recipient-123"] = RecipientProfile("recipient-123", "Jamie")

        viewModel.setRecipientMode(RecipientMode.SOMEONE_ELSE)
        viewModel.updateRecipientUidInput("recipient-123")
        viewModel.performRecipientLookup()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(RecipientLookupStatus.FOUND, state.lookupStatus)
        assertEquals("Jamie", state.resolvedProfile?.displayName)
        // Explicit confirmation must NOT happen automatically on lookup
        assertFalse(state.isRecipientConfirmed)

        // Now user taps Confirm
        viewModel.confirmRecipient()
        assertTrue(viewModel.uiState.value.isRecipientConfirmed)
    }

    @Test
    fun `performRecipientLookup - nonexistent UID sets nonexistent status and blocks confirmation`() {
        viewModel.setRecipientMode(RecipientMode.SOMEONE_ELSE)
        viewModel.updateRecipientUidInput("nonexistent-uid")
        viewModel.performRecipientLookup()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(RecipientLookupStatus.NONEXISTENT, state.lookupStatus)
        assertNull(state.resolvedProfile)
        assertFalse(state.isRecipientConfirmed)
    }

    @Test
    fun `staleAsyncLookupCallbackIsRejected - late lookup result ignored if input text changed`() {
        mockFirestoreAdapter.profiles["slow-uid"] = RecipientProfile("slow-uid", "Slow User")

        viewModel.setRecipientMode(RecipientMode.SOMEONE_ELSE)
        viewModel.updateRecipientUidInput("slow-uid")
        viewModel.performRecipientLookup()

        // User edits UID input while lookup is still in flight
        viewModel.updateRecipientUidInput("new-uid")

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        // State must remain for new-uid, not overwritten by slow-uid
        assertEquals("new-uid", state.recipientUidInput)
        assertNull(state.resolvedProfile)
        assertFalse(state.isRecipientConfirmed)
    }

    @Test
    fun `sessionChange - clears draft and recipient confirmation on user switch`() {
        viewModel.selectFutureMe("sender-uid")
        assertTrue(viewModel.uiState.value.isRecipientConfirmed)

        // Simulate session switch in Auth/SessionTracker
        mockAuthAdapter.signOut()
        sessionTracker.clearSession()
        mockAuthAdapter.setUid("new-user-uid")
        sessionTracker.startSession("new-user-uid")

        viewModel.syncSessionState()

        val state = viewModel.uiState.value
        assertEquals("new-user-uid", state.activeSessionUid)
        assertEquals("new-user-uid", state.recipientUidInput)
    }

    // --- S-05 Lock Mode & S-05 Validation Tests ---

    @Test
    fun `validateS05RecipientAndLocks - unconfirmed recipient is rejected`() {
        viewModel.updateText("Valid message")
        viewModel.setRecipientMode(RecipientMode.SOMEONE_ELSE)
        viewModel.updateRecipientUidInput("recipient-123")
        mockFirestoreAdapter.profiles["recipient-123"] = RecipientProfile("recipient-123", "Jamie")
        viewModel.performRecipientLookup()
        testDispatcher.scheduler.advanceUntilIdle()

        // Recipient found but NOT confirmed yet
        val res = viewModel.validateS05RecipientAndLocks()
        assertTrue(res is OperationResult.Invalid)
    }

    @Test
    fun `validateS05RecipientAndLocks - valid time lock passes when time in future`() {
        viewModel.updateText("Valid message")
        viewModel.selectFutureMe("sender-uid")
        viewModel.updateLockMode(LockMode.TIME)
        val futureTime = Instant.now().plusSeconds(3600)
        viewModel.updateUnlockAt(futureTime)

        val res = viewModel.validateS05RecipientAndLocks()
        assertTrue(res is OperationResult.Success)
    }

    @Test
    fun `validateS05RecipientAndLocks - past unlock time is rejected`() {
        viewModel.updateText("Valid message")
        viewModel.selectFutureMe("sender-uid")
        viewModel.updateLockMode(LockMode.TIME)
        val pastTime = Instant.now().minusSeconds(3600)
        viewModel.updateUnlockAt(pastTime)

        val res = viewModel.validateS05RecipientAndLocks()
        assertTrue(res is OperationResult.Invalid)
    }

    @Test
    fun `bothLocks - requires both future time and location target`() {
        viewModel.updateText("Valid message")
        viewModel.selectFutureMe("sender-uid")
        viewModel.updateLockMode(LockMode.TIME_AND_LOCATION)

        viewModel.updateUnlockAt(Instant.now().plusSeconds(3600))
        // Location target missing
        val resNoLoc = viewModel.validateS05RecipientAndLocks()
        assertTrue(resNoLoc is OperationResult.Invalid)

        viewModel.updateLocation(LocationTarget(37.5665, 126.9780, 150.0), "University garden")
        val resBoth = viewModel.validateS05RecipientAndLocks()
        assertTrue(resBoth is OperationResult.Success)
    }

    // --- S-06 Review & Send Notice Tests ---

    @Test
    fun `buildCapsuleDraft - creates complete CapsuleDraft matching all inputs`() {
        viewModel.updateTitle("Graduation Letter")
        viewModel.updateText("Dear future self")
        viewModel.attachPhoto("content://media/1.jpg", "1.jpg", 500000L)
        viewModel.selectFutureMe("sender-uid")
        viewModel.updateLockMode(LockMode.TIME)
        val futureTime = Instant.parse("2027-10-16T09:59:00Z")
        viewModel.updateUnlockAt(futureTime)

        val draft = viewModel.buildCapsuleDraft()

        assertEquals("Graduation Letter", draft.title)
        assertEquals("Dear future self", draft.text)
        assertEquals("content://media/1.jpg", draft.localPhotoUri)
        assertEquals("sender-uid", draft.recipientUid)
        assertTrue(draft.isRecipientConfirmed)
        assertEquals(LockMode.TIME, draft.lock.mode)
        assertEquals(futureTime, draft.lock.unlockAt)
    }

    @Test
    fun `triggerSendNotice - sets notice that publication belongs to downstream Issue 14`() {
        viewModel.triggerSendNotice()

        val notice = viewModel.uiState.value.sendNotice
        assertNotNull(notice)
        assertTrue(notice!!.contains("Issue #14"))
    }

    @Test
    fun `discardDraft - resets all draft state with new capsule ID`() {
        val initialId = viewModel.uiState.value.capsuleId
        viewModel.updateTitle("Some Title")
        viewModel.updateText("Some Text")

        viewModel.discardDraft()

        val state = viewModel.uiState.value
        assertTrue(state.title.isBlank())
        assertTrue(state.text.isBlank())
        assertTrue(state.capsuleId != initialId)
    }
}

// --- Fakes for Test Isolation ---

private class FakeAuthAdapter(initialUid: String?) : AuthAdapter {
    private var currentUid: String? = initialUid

    fun setUid(uid: String?) {
        currentUid = uid
    }

    override fun activeUid(): String? = currentUid
    override fun activeEmail(): String? = if (currentUid != null) "$currentUid@example.com" else null
    override fun activeDisplayName(): String? = if (currentUid != null) "Fake User" else null

    override suspend fun signIn(email: String, password: String): OperationResult<String> {
        currentUid = "signed-in-uid"
        return OperationResult.Success("signed-in-uid")
    }

    override suspend fun signUp(email: String, password: String, displayName: String): OperationResult<String> {
        currentUid = "signed-up-uid"
        return OperationResult.Success("signed-up-uid")
    }

    override suspend fun updateDisplayName(displayName: String): OperationResult<Unit> {
        return OperationResult.Success(Unit)
    }

    override fun signOut() {
        currentUid = null
    }
}

private class FakeFirestoreAdapter : FirestoreAdapter {
    val profiles = mutableMapOf<String, RecipientProfile>()

    override fun isAvailable(): Boolean = true
    override fun firestoreInstance(): FirebaseFirestore? = null

    override suspend fun getRecipientProfile(uid: String): OperationResult<RecipientProfile> {
        val profile = profiles[uid]
        return if (profile != null) {
            OperationResult.Success(profile)
        } else {
            OperationResult.Invalid("No recipient found with UID: $uid")
        }
    }

    override suspend fun saveRecipientProfile(profile: RecipientProfile): OperationResult<Unit> {
        profiles[profile.uid] = profile
        return OperationResult.Success(Unit)
    }
}
