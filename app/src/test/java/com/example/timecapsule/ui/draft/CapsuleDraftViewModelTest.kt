package com.example.timecapsule.ui.draft

import com.example.timecapsule.data.AccountSessionManager
import com.example.timecapsule.data.media.PhotoDraftStorage
import com.example.timecapsule.data.media.PhotoDraftRecord
import com.example.timecapsule.data.media.InvalidOriginalPhotoException
import com.example.timecapsule.domain.model.RetainedOriginalPhoto
import com.example.timecapsule.domain.model.OriginalPhotoFormat
import java.io.File
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
import kotlinx.coroutines.CompletableDeferred
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
        val attachRes = attachPhoto(1024L)
        assertTrue(attachRes is OperationResult.Success)

        val res = viewModel.validateS04Content()
        assertTrue(res is OperationResult.Success)
    }

    @Test
    fun `textAndPhotoAccepted - both text and photo pass validation`() {
        viewModel.updateText("A message with photo")
        attachPhoto(2048L)

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
        val res = attachPhoto(oversized)

        assertTrue(res is OperationResult.Invalid)
        assertNull(viewModel.uiState.value.localPhotoUri)
    }

    @Test
    fun `validPhotoAccepted - photo under 5 MiB is attached successfully`() {
        val validSize = 5 * 1024 * 1024L
        val res = attachPhoto(validSize)

        assertTrue(res is OperationResult.Success)
        assertEquals(File("/private/photo.jpg").toURI().toString(), viewModel.uiState.value.localPhotoUri)
        assertEquals(validSize, viewModel.uiState.value.originalPhoto?.sizeBytes)
    }

    /** Supplies a validated-storage fixture; real byte limits are tested by OriginalPhotoStoreTest. */
    private fun attachPhoto(size: Long): OperationResult<Unit> {
        viewModel.selectPhoto(FakePhotoStorage(size), "content://fixture")
        testDispatcher.scheduler.advanceUntilIdle()
        return if (viewModel.uiState.value.originalPhoto != null) OperationResult.Success(Unit)
            else OperationResult.Invalid("Rejected by photo storage")
    }

    /** Removing a selection while its provider is reading prevents a late photo from reappearing. */
    @Test fun removingPhotoInvalidatesPendingSelection() {
        val gate = CompletableDeferred<Unit>()
        val delegate = FakePhotoStorage(3)
        val storage = object : PhotoDraftStorage by delegate {
            /** Waits for a simulated slow provider before returning the validated fixture. */
            override suspend fun retain(uid: String, capsuleId: String, uri: String): RetainedOriginalPhoto {
                gate.await()
                return delegate.retain(uid, capsuleId, uri)
            }
        }
        viewModel.selectPhoto(storage, "content://slow")
        testDispatcher.scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.photoBusy)
        viewModel.removePhoto()
        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.originalPhoto)
        assertFalse(viewModel.uiState.value.photoBusy)
    }

    /** An old account's pending photo never becomes visible in a new account draft. */
    @Test fun accountChangeInvalidatesPendingPhoto() {
        val gate = CompletableDeferred<Unit>()
        val delegate = FakePhotoStorage(3)
        val storage = object : PhotoDraftStorage by delegate {
            /** Holds the old account's selection until after account switching. */
            override suspend fun retain(uid: String, capsuleId: String, uri: String): RetainedOriginalPhoto {
                gate.await()
                return delegate.retain(uid, capsuleId, uri)
            }
        }
        viewModel.selectPhoto(storage, "content://slow")
        testDispatcher.scheduler.runCurrent()
        mockAuthAdapter.setUid("another-user")
        viewModel.syncSessionState()
        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("another-user", viewModel.uiState.value.activeSessionUid)
        assertNull(viewModel.uiState.value.originalPhoto)
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
    fun `staleAsyncLookupCallbackIsRejected_whenUidChangedAndChangedBack`() {
        mockFirestoreAdapter.profiles["uid-a"] = RecipientProfile("uid-a", "User A")

        viewModel.setRecipientMode(RecipientMode.SOMEONE_ELSE)
        viewModel.updateRecipientUidInput("uid-a")
        viewModel.performRecipientLookup() // Token 1

        viewModel.updateRecipientUidInput("uid-b") // Token 2
        viewModel.updateRecipientUidInput("uid-a") // Token 3

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        // Token 1 result was rejected because activeLookupToken moved to 3
        assertEquals("uid-a", state.recipientUidInput)
        assertNull(state.resolvedProfile)
        assertFalse(state.isRecipientConfirmed)
    }

    @Test
    fun `lookup_distinctErrorResult_handledForNetworkAndDenied`() {
        mockFirestoreAdapter.nextError = OperationResult.Unavailable("Network connection lost")

        viewModel.setRecipientMode(RecipientMode.SOMEONE_ELSE)
        viewModel.updateRecipientUidInput("uid-offline")
        viewModel.performRecipientLookup()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(RecipientLookupStatus.ERROR, state.lookupStatus)
        assertTrue(state.lookupErrorMessage!!.contains("Network"))
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
        attachPhoto(500000L)
        viewModel.selectFutureMe("sender-uid")
        viewModel.updateLockMode(LockMode.TIME)
        val futureTime = Instant.parse("2027-10-16T09:59:00Z")
        viewModel.updateUnlockAt(futureTime)

        val draft = viewModel.buildCapsuleDraft()

        assertEquals("Graduation Letter", draft.title)
        assertEquals("Dear future self", draft.text)
        assertEquals(File("/private/photo.jpg").toURI().toString(), draft.localPhotoUri)
        assertNotNull(draft.originalPhoto)
        assertEquals("sender-uid", draft.recipientUid)
        assertTrue(draft.isRecipientConfirmed)
        assertEquals(LockMode.TIME, draft.lock.mode)
        assertEquals(futureTime, draft.lock.unlockAt)
    }

    @Test
    fun `triggerSendNotice - sets user-facing notice that sending is unavailable`() {
        viewModel.triggerSendNotice()

        val notice = viewModel.uiState.value.sendNotice
        assertNotNull(notice)
        assertTrue(notice!!.contains("Sending isn't available yet"))
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

/** Emulates validated input without requiring Android's URI implementation. */
private class FakePhotoStorage(private val size: Long) : PhotoDraftStorage {
    /** Rejects invalid fixtures or returns retained metadata for the requested identity. */
    override suspend fun retain(uid: String, capsuleId: String, uri: String): RetainedOriginalPhoto {
        if (size !in 1L..5_242_880L) throw InvalidOriginalPhotoException("Invalid size")
        return RetainedOriginalPhoto(uid, capsuleId, "fixture.original", OriginalPhotoFormat.JPEG,
            size, "fixture-hash")
    }
    /** Supplies a stable private path for mapping assertions. */
    override fun file(photo: RetainedOriginalPhoto): File = File("/private/photo.jpg")
    /** This fixture owns no actual files. */
    override suspend fun remove(photo: RetainedOriginalPhoto) {}
    /** Persistence has separate real-filesystem tests. */
    override suspend fun save(uid: String, draft: PhotoDraftRecord) {}
    /** Starts without a saved draft. */
    override suspend fun restore(uid: String): PhotoDraftRecord? = null
}

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
    var nextError: OperationResult<RecipientProfile>? = null

    override fun isAvailable(): Boolean = true
    override fun firestoreInstance(): FirebaseFirestore? = null

    override suspend fun getRecipientProfile(uid: String): OperationResult<RecipientProfile> {
        val err = nextError
        if (err != null) {
            nextError = null
            return err
        }
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
