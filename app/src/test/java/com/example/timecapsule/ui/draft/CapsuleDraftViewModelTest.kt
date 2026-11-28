package com.example.timecapsule.ui.draft

import com.example.timecapsule.data.session.StorageSession
import com.example.timecapsule.R
import com.example.timecapsule.data.AccountSessionManager
import com.example.timecapsule.data.CapsuleRepository
import com.example.timecapsule.data.DefaultCapsuleRepository
import com.example.timecapsule.domain.model.CapsuleDraft
import com.example.timecapsule.domain.model.CapsuleLock
import com.example.timecapsule.domain.model.FrozenCapsuleOperation
import com.example.timecapsule.domain.model.PublicationPhase
import com.example.timecapsule.domain.model.RecoveredPublication
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import com.example.timecapsule.domain.session.SessionSnapshot
import com.example.timecapsule.ui.s06.reviewCondition
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

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
        accountSessionManager = AccountSessionManager(mockAuthAdapter, mockFirestoreAdapter, sessionTracker, storageDispatcher = testDispatcher)
        runTest(testDispatcher) { accountSessionManager.restoreSessionIfAuthenticated() }

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
            override suspend fun retain(session: StorageSession, uid: String, capsuleId: String, uri: String): RetainedOriginalPhoto {
                gate.await()
                return delegate.retain(session, uid, capsuleId, uri)
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
            override suspend fun retain(session: StorageSession, uid: String, capsuleId: String, uri: String): RetainedOriginalPhoto {
                gate.await()
                return delegate.retain(session, uid, capsuleId, uri)
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
    fun `updateUnlockAt preserves explicit creationZone in uiState`() {
        val futureTime = Instant.now().plusSeconds(3600)
        viewModel.updateUnlockAt(futureTime, "America/New_York")

        val state = viewModel.uiState.value
        assertEquals(futureTime, state.unlockAt)
        assertEquals("America/New_York", state.creationZone)
    }

    @Test
    fun `sendCapsule_suspendedOperation_accountSwitchBeforeExecution_suppressesRepositoryCall`() = runTest(testDispatcher) {
        val repoGate = CompletableDeferred<OperationResult<String>>()
        var publishCallCount = 0

        val customRepo = object : CapsuleRepository by DefaultCapsuleRepository(sessionTracker, mockAuthAdapter, mockFirestoreAdapter) {
            override suspend fun publish(frozenOperation: CapsuleDraft, targetSession: SessionSnapshot): OperationResult<String> {
                if (!sessionTracker.isSessionValid(targetSession)) return OperationResult.Denied("Account session changed.")
                publishCallCount++
                return repoGate.await()
            }
        }

        val testViewModel = CapsuleDraftViewModel(accountSessionManager, customRepo).apply {
            updateText("Message to submit")
            updateLockMode(LockMode.TIME)
            updateUnlockAt(Instant.now().plusSeconds(3600))
            selectFutureMe("sender-uid")
        }

        // 1. Initiate send operation under user-1 (queued on StandardTestDispatcher)
        testViewModel.sendCapsule()

        // 2. Switch account session to user-2 before coroutine progresses to repository.publish
        accountSessionManager.signOut()
        mockAuthAdapter = FakeAuthAdapter(initialUid = "user-2")
        accountSessionManager = AccountSessionManager(mockAuthAdapter, mockFirestoreAdapter, sessionTracker, storageDispatcher = testDispatcher)
        sessionTracker.startSession("user-2")

        // 3. Release coroutines
        runCurrent()
        repoGate.complete(OperationResult.Success("capsule-1"))
        runCurrent()

        // 4. Assert that stale work did not invoke the repository under user-2
        assertEquals(0, publishCallCount)
    }

    /** Controlled repository keeps UI race tests separate from the coordinator's disk/network tests. */
    private inner class PublicationRepository : CapsuleRepository by
        DefaultCapsuleRepository(sessionTracker, mockAuthAdapter, mockFirestoreAdapter) {
        var published = mutableListOf<CapsuleDraft>()
        var retried = mutableListOf<String>()
        val abandoned = mutableListOf<String>()
        var recovered: OperationResult<List<RecoveredPublication>> = OperationResult.Success(emptyList())
        var onPublish: suspend (CapsuleDraft) -> OperationResult<String> = {
            OperationResult.OutcomeUnknown(it.capsuleId, "Uncertain")
        }
        var onRecover: (suspend () -> OperationResult<List<RecoveredPublication>>)? = null
        var replacement: CapsuleDraft? = null

        /** Records submitted values and allows a delayed acknowledgment. */
        override suspend fun publish(frozenOperation: CapsuleDraft, targetSession: SessionSnapshot): OperationResult<String> {
            if (!sessionTracker.isSessionValid(targetSession)) return OperationResult.Denied("Account session changed.")
            published.add(frozenOperation)
            return onPublish(frozenOperation)
        }
        override suspend fun publish(frozenOperation: CapsuleDraft): OperationResult<String> =
            publish(frozenOperation, sessionTracker.currentSession())

        /** Returns independently controlled recovery evidence. */
        override suspend fun recoverPublications(targetSession: SessionSnapshot): OperationResult<List<RecoveredPublication>> {
            if (!sessionTracker.isSessionValid(targetSession)) return OperationResult.Denied("Account session changed.")
            return onRecover?.invoke() ?: recovered
        }
        override suspend fun recoverPublications() =
            recoverPublications(sessionTracker.currentSession())

        /** Records unchanged retry identity without creating a second UI snapshot. */
        override suspend fun retryPublication(capsuleId: String, targetSession: SessionSnapshot): OperationResult<String> {
            if (!sessionTracker.isSessionValid(targetSession)) return OperationResult.Denied("Account session changed.")
            retried.add(capsuleId)
            return OperationResult.OutcomeUnknown(capsuleId, "Uncertain")
        }
        override suspend fun retryPublication(capsuleId: String): OperationResult<String> =
            retryPublication(capsuleId, sessionTracker.currentSession())

        /** Returns a coordinator-approved replacement fixture. */
        override suspend fun abandonPublication(capsuleId: String, targetSession: SessionSnapshot): OperationResult<CapsuleDraft> {
            if (!sessionTracker.isSessionValid(targetSession)) return OperationResult.Denied("Account session changed.")
            abandoned.add(capsuleId)
            return replacement?.let { OperationResult.Success(it) } ?: OperationResult.Unavailable("No copy")
        }
        override suspend fun abandonPublication(capsuleId: String): OperationResult<CapsuleDraft> =
            abandonPublication(capsuleId, sessionTracker.currentSession())
    }

    /** Creates a valid location-only draft without dependence on the wall clock. */
    private fun publicationModel(repository: PublicationRepository): CapsuleDraftViewModel =
        CapsuleDraftViewModel(accountSessionManager, repository).also {
            it.updateText("Frozen message")
            it.updateLockMode(LockMode.LOCATION)
            it.updateLocation(LocationTarget(37.0, 127.0), "Place")
        }

    /** Uses a real frozen snapshot as recovery evidence for ViewModel state assertions. */
    private fun recovered(model: CapsuleDraftViewModel, phase: PublicationPhase,
        canAbandon: Boolean = false): RecoveredPublication {
        if (phase == PublicationPhase.SENT) {
            return RecoveredPublication(model.uiState.value.capsuleId, phase, null, false)
        }
        val operation = FrozenCapsuleOperation.freeze(model.buildCapsuleDraft(),
            "sender-uid", "UTC", Instant.now())
        return RecoveredPublication(operation.capsuleId, phase,
            if (phase == PublicationPhase.SENT) null else operation, canAbandon)
    }

    /** A target selected after switching accounts cannot inherit the previous compose state. */
    @Test fun targetedRecoveryClearsPreviousAccountBeforeQuery() {
        val repository = PublicationRepository()
        val model = publicationModel(repository)
        mockAuthAdapter.setUid("recipient-b")
        sessionTracker.startSession("recipient-b")
        model.recoverPublicationState("requested")
        val state = model.uiState.value
        assertEquals("recipient-b", state.activeSessionUid)
        assertEquals("", state.text)
        assertNull(state.locationTarget)
        assertTrue(state.recoveryBlocked)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(model.uiState.value.recoveryBlocked)
        assertFalse(model.uiState.value.editable)
    }

    /** A missing recovery record represents unavailable recovery without claiming definite failure or allowing abandonment. */
    @Test fun missingRecoveryRecord_doesNotSetPublicationPhaseFailedOrAllowAbandonment() {
        val repository = PublicationRepository()
        val model = publicationModel(repository)
        repository.recovered = OperationResult.Success(emptyList())

        model.recoverPublicationState("missing-capsule-id")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = model.uiState.value
        assertEquals("missing-capsule-id", state.capsuleId)
        assertTrue(state.recoveryBlocked)
        assertTrue(state.statusOnly)
        assertFalse(state.canAbandon)
        assertFalse(state.canRetryUnchanged)
        assertNull(state.publicationPhase)
    }

    /** Recovering an operation with a different location target through the ViewModel flow clears any stale location label. */
    @Test fun recoveringDifferentOperationClearsStaleLocationLabelThroughViewModelFlow() {
        val repository = PublicationRepository()
        val model = publicationModel(repository)

        // 1. Create a draft with an existing location label "Eiffel Tower"
        model.updateLocation(LocationTarget(48.8584, 2.2945), "Eiffel Tower")

        // 2. Create another frozen operation with different coordinates (Seoul Plaza) and no location name
        val olderDraft = CapsuleDraft(
            capsuleId = "older-capsule-101",
            recipientUid = "recipient-b",
            text = "Older location capsule",
            localPhotoUri = null,
            lock = CapsuleLock(LockMode.LOCATION, location = LocationTarget(37.5665, 126.9780)),
            title = null,
            recipientDisplayName = "recipient-b",
            isRecipientConfirmed = true,
            locationName = null,
            originalPhoto = null,
            creationZone = "Asia/Seoul"
        )
        val olderOperation = FrozenCapsuleOperation.freeze(olderDraft, "sender-uid", "Asia/Seoul", Instant.now())
        val recoveredRecord = RecoveredPublication(
            capsuleId = "older-capsule-101",
            phase = PublicationPhase.FAILED,
            snapshot = olderOperation,
            canAbandon = true
        )

        // 3. Supply recovered operation and invoke real recovery path
        repository.recovered = OperationResult.Success(listOf(recoveredRecord))
        model.recoverPublicationState("older-capsule-101")
        testDispatcher.scheduler.advanceUntilIdle()

        // 4. Examine resulting ViewModel state and review text from reviewCondition
        val state = model.uiState.value
        assertEquals("older-capsule-101", state.capsuleId)
        val reviewText = reviewCondition(state)
        assertTrue(reviewText.contains("37.5665"))
        assertFalse(reviewText.contains("Eiffel Tower"))
    }

    /** Reauthentication invalidates even a previously verified target under the same UID. */
    @Test fun targetedRetryRejectsChangedGeneration() {
        val repository = PublicationRepository()
        val model = publicationModel(repository)
        val record = recovered(model, PublicationPhase.FAILED, true)
        repository.recovered = OperationResult.Success(listOf(record))
        model.recoverPublicationState(record.capsuleId)
        testDispatcher.scheduler.advanceUntilIdle()
        sessionTracker.clearSession()
        sessionTracker.startSession("sender-uid")
        model.retryPublication()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(repository.retried.isEmpty())
    }

    /** Rechecking the same selection cannot rebind it to a later login generation. */
    @Test fun targetedRecheckKeepsOriginalGeneration() {
        val repository = PublicationRepository()
        val model = publicationModel(repository)
        val record = recovered(model, PublicationPhase.FAILED, true)
        repository.recovered = OperationResult.Success(listOf(record))
        model.recoverPublicationState(record.capsuleId)
        testDispatcher.scheduler.advanceUntilIdle()
        sessionTracker.startSession("sender-uid")
        model.recoverPublicationState(record.capsuleId)
        testDispatcher.scheduler.advanceUntilIdle()
        model.retryPublication()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(repository.retried.isEmpty())
        assertTrue(model.uiState.value.recoveryBlocked)
    }

    /** A late answer for X cannot replace an already selected and verified Y. */
    @Test fun targetedSelectionSupersedesPendingRecovery() {
        val repository = PublicationRepository()
        val model = publicationModel(repository)
        val old = recovered(model, PublicationPhase.FAILED, true)
        val requested = RecoveredPublication("requested-y", PublicationPhase.OUTCOME_UNKNOWN, null, false)
        val delayed = CompletableDeferred<OperationResult<List<RecoveredPublication>>>()
        repository.onRecover = { delayed.await() }
        model.recoverPublicationState(old.capsuleId)
        testDispatcher.scheduler.runCurrent()
        repository.onRecover = { OperationResult.Success(listOf(requested)) }
        model.recoverPublicationState(requested.capsuleId)
        testDispatcher.scheduler.runCurrent()
        delayed.complete(OperationResult.Success(listOf(old)))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(requested.capsuleId, model.uiState.value.capsuleId)
        model.retryPublication()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(requested.capsuleId), repository.retried)
    }

    /** A failed selection cannot retry the self-send that previously occupied this screen. */
    @Test fun targetedFailureCannotRetryPreviousOperation() = checkFailedTarget(false)

    /** Replace must also use the requested, verified operation and its originating session. */
    @Test fun targetedFailureCannotReplacePreviousOperation() = checkFailedTarget(true)

    /** Exercises both publication controls after a status-only X is superseded by Y. */
    private fun checkFailedTarget(replace: Boolean) {
        val repository = PublicationRepository()
        val model = publicationModel(repository)
        val record = recovered(model, PublicationPhase.FAILED, true)
        repository.recovered = OperationResult.Success(listOf(record))
        model.recoverPublicationState(record.capsuleId)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(model.uiState.value.canAbandon)
        repository.recovered = OperationResult.Denied("Target unavailable")
        model.recoverPublicationState("requested-y")
        testDispatcher.scheduler.advanceUntilIdle()
        if (replace) model.replaceFailedPublication() else model.retryPublication()
        model.sendCapsule()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(repository.retried.isEmpty())
        assertTrue(repository.abandoned.isEmpty())
        assertTrue(repository.published.isEmpty())
        assertEquals("requested-y", model.uiState.value.capsuleId)
        assertTrue(model.uiState.value.recoveryBlocked)
    }

    /** Double taps, discard and editing cannot mutate a submission while the backend is waiting. */
    @Test fun submissionFreezesBeforeSuspensionAndRejectsDuplicateTaps() {
        val repository = PublicationRepository()
        val ack = CompletableDeferred<OperationResult<String>>()
        repository.onPublish = { ack.await() }
        val model = publicationModel(repository)
        val id = model.uiState.value.capsuleId
        val pending = recovered(model, PublicationPhase.OUTCOME_UNKNOWN)
        model.sendCapsule()
        model.sendCapsule()
        model.updateText("Changed")
        model.updateTitle("Changed")
        model.removePhoto()
        model.discardDraft()
        model.updateRecipientUidInput("another")
        testDispatcher.scheduler.runCurrent()
        assertEquals(1, repository.published.size)
        assertEquals(id, repository.published.single().capsuleId)
        assertEquals("", model.uiState.value.text)
        assertEquals("Frozen message", repository.published.single().text)
        assertTrue(model.uiState.value.publicationBusy)
        repository.recovered = OperationResult.Success(listOf(pending))
        ack.complete(OperationResult.OutcomeUnknown(id, "Uncertain"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(model.uiState.value.editable)
    }

    @Test fun committedSelfSendWithLostAckStaysStatusOnlyAcrossFailedRecovery() {
        val failures: List<suspend () -> OperationResult<List<RecoveredPublication>>> = listOf(
            { OperationResult.Unavailable("Offline") },
            { throw java.io.IOException("Recovery failed") },
            { kotlinx.coroutines.awaitCancellation() },
            { OperationResult.Success(emptyList()) }
        )
        for (failure in failures) {
            val repository = PublicationRepository()
            val model = publicationModel(repository)
            model.updateTitle("Private title")
            model.selectPhoto(FakePhotoStorage(3), "content://private-photo")
            testDispatcher.scheduler.advanceUntilIdle()
            val remote = mutableMapOf<String, CapsuleDraft>()
            repository.onPublish = { draft ->
                remote[draft.capsuleId] = draft
                OperationResult.OutcomeUnknown(draft.capsuleId, "Acknowledgment lost")
            }
            repository.onRecover = failure
            val id = model.uiState.value.capsuleId
            model.sendCapsule()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals("Frozen message", remote.getValue(id).text)
            assertNotNull(remote.getValue(id).originalPhoto)
            fun checkStatus() {
                val state = model.uiState.value
                assertTrue(state.statusOnly)
                assertFalse(state.editable)
                assertFalse(state.canAbandon)
                assertFalse(state.publicationBusy)
                assertEquals(PublicationPhase.OUTCOME_UNKNOWN, state.publicationPhase)
                assertEquals("", state.text)
                assertEquals("", state.title)
                assertNull(state.originalPhoto)
                assertNull(state.localPhotoUri)
                assertNull(state.photoName)
                assertNull(state.photoSizeBytes)
            }
            checkStatus()
            model.recoverPublicationState(restoreEditable = true)
            testDispatcher.scheduler.advanceUntilIdle()
            checkStatus()
            model.retryPublication()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(listOf(id), repository.retried)
            assertEquals(1, repository.published.size)
            checkStatus()
        }
    }

    /** Confirmed publication removes protected compose values and only then allows a new ID. */
    @Test fun confirmedSuccessClearsContentAndAllowsBlankNewDraft() {
        val repository = PublicationRepository()
        val model = publicationModel(repository)
        val id = model.uiState.value.capsuleId
        repository.onPublish = { OperationResult.Success(id) }
        repository.recovered = OperationResult.Success(listOf(recovered(model, PublicationPhase.SENT)))
        model.sendCapsule()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("", model.uiState.value.text)
        assertNull(model.uiState.value.localPhotoUri)
        assertEquals(PublicationPhase.SENT, model.uiState.value.publicationPhase)
        model.updateText("Not editable")
        assertEquals("", model.uiState.value.text)
        model.startNewAfterSent()
        assertTrue(model.uiState.value.editable)
        assertTrue(id != model.uiState.value.capsuleId)
    }

    /** Foreground recovery restores immutable content; retries pass only the saved identity. */
    @Test fun recoveredUnknownCanRetryButCannotEditOrDiscard() {
        val repository = PublicationRepository()
        val model = publicationModel(repository)
        val self = recovered(model, PublicationPhase.OUTCOME_UNKNOWN)
        val record = self.copy(snapshot = self.snapshot!!.copy(recipientUid = "another-recipient"))
        repository.recovered = OperationResult.Success(listOf(record))
        model.recoverPublicationState()
        testDispatcher.scheduler.advanceUntilIdle()
        model.updateText("Changed")
        model.discardDraft()
        assertEquals(record.capsuleId, model.uiState.value.capsuleId)
        assertEquals("Frozen message", model.uiState.value.text)
        assertFalse(model.uiState.value.canAbandon)
        model.retryPublication()
        model.retryPublication()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(record.capsuleId), repository.retried)
    }

    /** A definite failed record may produce a new editable copy with no recipient confirmation. */
    @Test fun failedReplacementResetsRecipientConfirmation() {
        val repository = PublicationRepository()
        val model = publicationModel(repository)
        repository.recovered = OperationResult.Success(listOf(recovered(model, PublicationPhase.FAILED, true)))
        repository.replacement = model.buildCapsuleDraft().copy(capsuleId = "new-copy",
            isRecipientConfirmed = false)
        model.recoverPublicationState()
        testDispatcher.scheduler.advanceUntilIdle()
        model.replaceFailedPublication()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("new-copy", model.uiState.value.capsuleId)
        assertEquals("Frozen message", model.uiState.value.text)
        assertTrue(model.uiState.value.editable)
        assertFalse(model.uiState.value.isRecipientConfirmed)
    }

    /** Failed startup recovery cannot silently fall back to a fresh send. */
    @Test fun unavailableRecoveryBlocksEditingAndPublication() {
        val repository = PublicationRepository()
        repository.recovered = OperationResult.Unavailable("Offline")
        val model = publicationModel(repository)
        model.recoverPublicationState()
        testDispatcher.scheduler.advanceUntilIdle()
        model.sendCapsule()
        model.updateText("Changed")
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(model.uiState.value.recoveryBlocked)
        assertFalse(model.uiState.value.editable)
        assertTrue(repository.published.isEmpty())
    }

    /** A hung read reaches the explicit blocked state rather than trapping the screen forever. */
    @Test fun recoveryTimeoutPreservesFrozenState() {
        val repository = PublicationRepository()
        val never = CompletableDeferred<OperationResult<List<RecoveredPublication>>>()
        repository.onRecover = { never.await() }
        val model = publicationModel(repository)
        model.recoverPublicationState()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(model.uiState.value.recoveryBusy)
        assertTrue(model.uiState.value.recoveryBlocked)
        assertEquals("Frozen message", model.uiState.value.text)
        never.complete(OperationResult.Success(emptyList()))
    }

    /** Foreground checks of the same account preserve the session generation of an active send. */
    @Test fun foregroundCheckDoesNotInvalidateCurrentSend() {
        val repository = PublicationRepository()
        val ack = CompletableDeferred<OperationResult<String>>()
        repository.onPublish = { ack.await() }
        val model = publicationModel(repository)
        val session = sessionTracker.currentSession()
        model.sendCapsule()
        testDispatcher.scheduler.runCurrent()
        model.syncSessionState()
        assertEquals(session, sessionTracker.currentSession())
        repository.recovered = OperationResult.Success(listOf(recovered(model, PublicationPhase.SENT)))
        ack.complete(OperationResult.Success(model.uiState.value.capsuleId))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(PublicationPhase.SENT, model.uiState.value.publicationPhase)
    }

    /** Reauthentication to the same UID is still a new generation; old callbacks must be ignored. */
    @Test fun oldAcknowledgmentCannotRepaintReauthenticatedAccount() {
        val repository = PublicationRepository()
        val ack = CompletableDeferred<OperationResult<String>>()
        repository.onPublish = { ack.await() }
        val model = publicationModel(repository)
        model.sendCapsule()
        testDispatcher.scheduler.runCurrent()
        sessionTracker.clearSession()
        sessionTracker.startSession("sender-uid")
        model.syncSessionState()
        testDispatcher.scheduler.runCurrent()
        ack.complete(OperationResult.Success("old-id"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(model.uiState.value.publicationPhase)
        assertEquals("", model.uiState.value.text)
    }

    /** Validation rejected before any saved operation leaves the draft editable after recovery. */
    @Test fun rejectedPreflightWithoutSavedSendCanBeEdited() {
        val repository = PublicationRepository()
        repository.onPublish = { OperationResult.Invalid("Preflight") }
        val model = publicationModel(repository)
        model.sendCapsule()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(model.uiState.value.editable)
        assertEquals("Frozen message", model.uiState.value.text)
    }

    /** A completed send stays locked while compose cleanup is still awaiting recovery. */
    @Test fun newDraftIsBlockedUntilPostSendRecoveryReturns() {
        val repository = PublicationRepository()
        val cleanup = CompletableDeferred<OperationResult<List<RecoveredPublication>>>()
        repository.onRecover = { cleanup.await() }
        val model = publicationModel(repository)
        val id = model.uiState.value.capsuleId
        repository.onPublish = { OperationResult.Success(id) }
        model.sendCapsule()
        testDispatcher.scheduler.runCurrent()
        model.startNewAfterSent()
        assertEquals(id, model.uiState.value.capsuleId)
        assertTrue(model.uiState.value.publicationBusy)
        cleanup.complete(OperationResult.Success(listOf(recovered(model, PublicationPhase.SENT))))
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(model.uiState.value.publicationBusy)
    }

    /** Startup must not briefly load raw sent text from the editable journal before reconciliation. */
    @Test fun sentRecoveryDoesNotReadOldEditableContent() {
        val repository = PublicationRepository()
        val model = publicationModel(repository)
        var reads = 0
        val storage = object : PhotoDraftStorage by FakePhotoStorage(1L) {
            override suspend fun restore(uid: String): PhotoDraftRecord {
                reads++
                return PhotoDraftRecord("old", "", "must stay hidden", null)
            }
        }
        repository.recovered = OperationResult.Success(listOf(recovered(model, PublicationPhase.SENT)))
        model.configurePhotos(storage)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, reads)
        assertEquals("", model.uiState.value.text)
        assertFalse(model.uiState.value.editable)
    }

    /** When recovery becomes available, retrying startup restores the existing draft, not a blank one. */
    @Test fun failedStartupCanRecoverOriginalEditableDraft() {
        val repository = PublicationRepository()
        val model = CapsuleDraftViewModel(accountSessionManager, repository)
        val storage = object : PhotoDraftStorage by FakePhotoStorage(1L) {
            override suspend fun restore(uid: String) =
                PhotoDraftRecord("saved-draft", "Title", "kept locally", null)
        }
        repository.recovered = OperationResult.Unavailable("Offline")
        model.configurePhotos(storage)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(model.uiState.value.recoveryBlocked)
        repository.recovered = OperationResult.Success(emptyList())
        model.recoverPublicationState(restoreEditable = true)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("saved-draft", model.uiState.value.capsuleId)
        assertEquals("kept locally", model.uiState.value.text)
        assertTrue(model.uiState.value.editable)
    }

    /** Recovery of an elapsed time lock must retry its old ID rather than demand an edited date. */
    @Test fun elapsedFrozenTimeDoesNotBlockUnchangedRetry() {
        val repository = PublicationRepository()
        val model = publicationModel(repository)
        val oldTime = Instant.parse("2020-01-02T00:00:00Z")
        val operation = FrozenCapsuleOperation.freeze(model.buildCapsuleDraft().copy(recipientUid = "another-recipient",
            lock = com.example.timecapsule.domain.model.CapsuleLock(LockMode.TIME, oldTime)),
            "sender-uid", "UTC", Instant.parse("2020-01-01T00:00:00Z"))
        repository.recovered = OperationResult.Success(listOf(RecoveredPublication(
            operation.capsuleId, PublicationPhase.OUTCOME_UNKNOWN, operation, false)))
        model.recoverPublicationState()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(model.validateS05RecipientAndLocks() is OperationResult.Success)
        model.retryPublication()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(operation.capsuleId), repository.retried)
        assertEquals(oldTime, model.uiState.value.unlockAt)
    }

    /** A foreground recovery result for the previous account cannot leak a frozen message. */
    @Test fun accountSwitchDiscardsDelayedRecovery() {
        val repository = PublicationRepository()
        val model = publicationModel(repository)
        val oldRecord = recovered(model, PublicationPhase.OUTCOME_UNKNOWN)
        val delayed = CompletableDeferred<OperationResult<List<RecoveredPublication>>>()
        repository.onRecover = { delayed.await() }
        model.recoverPublicationState()
        testDispatcher.scheduler.runCurrent()
        mockAuthAdapter.setUid("other-user")
        repository.onRecover = null
        model.syncSessionState()
        testDispatcher.scheduler.runCurrent()
        delayed.complete(OperationResult.Success(listOf(oldRecord)))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("other-user", model.uiState.value.activeSessionUid)
        assertEquals("", model.uiState.value.text)
        assertNull(model.uiState.value.publicationPhase)
    }

    /** A local cleanup/read exception cannot revoke a publication acknowledgment already received. */
    @Test fun cleanupFailureDoesNotDowngradeConfirmedSuccess() {
        val repository = PublicationRepository()
        val model = publicationModel(repository)
        repository.onPublish = { OperationResult.Success(it.capsuleId) }
        repository.onRecover = { throw java.io.IOException("Cleanup unavailable") }
        model.sendCapsule()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(PublicationPhase.SENT, model.uiState.value.publicationPhase)
        assertEquals("", model.uiState.value.text)
        assertFalse(model.uiState.value.publicationBusy)
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

    // --- Correction B Race & Lifecycle Tests ---

    @Test
    fun `discardWhilePhotoRetentionUnfinished - removes abandoned photo and resets state`() {
        val retainGate = CompletableDeferred<Unit>()
        val storage = TestablePhotoStorage(retainGate = retainGate)

        viewModel.selectPhoto(storage, "slow-uri")
        testDispatcher.scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.photoBusy)

        val oldCapsuleId = viewModel.uiState.value.capsuleId
        viewModel.discardDraft()

        retainGate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.originalPhoto)
        assertFalse(state.photoBusy)
        assertTrue(state.capsuleId != oldCapsuleId)
        assertEquals(1, storage.removedPhotos.size)
        assertEquals("file-slow-uri.original", storage.removedPhotos.first().privateFileName)
    }

    @Test
    fun `discardOrRemovalWhileJournalSaveIsSuspended - cleans up photo and preserves discarded state`() {
        val saveGate = CompletableDeferred<Unit>()
        val storage = TestablePhotoStorage(saveGate = saveGate)

        viewModel.selectPhoto(storage, "slow-save-uri")
        testDispatcher.scheduler.advanceUntilIdle()

        // Photo retained, now suspended waiting on saveGate
        viewModel.removePhoto()

        saveGate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.originalPhoto)
        assertFalse(state.photoBusy)
        assertTrue(storage.removedPhotos.any { it.privateFileName == "file-slow-save-uri.original" })
    }

    @Test
    fun `accountSwitchDuringPendingPhotoPersistence - invalidates selection and cleans up`() {
        val saveGate = CompletableDeferred<Unit>()
        val storage = TestablePhotoStorage(saveGate = saveGate)

        viewModel.selectPhoto(storage, "pending-account-uri")
        testDispatcher.scheduler.advanceUntilIdle()

        mockAuthAdapter.setUid("new-user")
        sessionTracker.startSession("new-user")
        viewModel.syncSessionState()

        saveGate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("new-user", state.activeSessionUid)
        assertNull(state.originalPhoto)
        assertTrue(storage.removedPhotos.any { it.privateFileName == "file-pending-account-uri.original" })
    }

    @Test
    fun `obsoletePhotoSelectionCompletingAfterNewerSelection - is ignored and cleaned up`() {
        val retainGate1 = CompletableDeferred<Unit>()
        val storage1 = TestablePhotoStorage(retainGate = retainGate1)

        viewModel.selectPhoto(storage1, "photo-1")
        testDispatcher.scheduler.runCurrent()

        val storage2 = TestablePhotoStorage()
        viewModel.selectPhoto(storage2, "photo-2")
        testDispatcher.scheduler.advanceUntilIdle()

        // Now photo 1 completes late
        retainGate1.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.originalPhoto)
        assertEquals("file-photo-2.original", state.originalPhoto?.privateFileName)
        assertTrue(storage1.removedPhotos.any { it.privateFileName == "file-photo-1.original" })
    }

    @Test
    fun `lateDraftRestorationAfterStateChanges - does not overwrite user edits`() {
        val restoreGate = CompletableDeferred<PhotoDraftRecord?>()
        val storage = TestablePhotoStorage(restoreGate = restoreGate)

        viewModel.configurePhotos(storage)
        testDispatcher.scheduler.runCurrent()

        // User edits text while restore is in flight
        viewModel.updateText("New User Text")

        val savedRecord = PhotoDraftRecord(
            capsuleId = viewModel.uiState.value.capsuleId,
            title = "Old Saved Title",
            text = "Old Saved Text",
            photo = RetainedOriginalPhoto("sender-uid", viewModel.uiState.value.capsuleId, "old.original", OriginalPhotoFormat.JPEG, 1024L, "hash")
        )
        restoreGate.complete(savedRecord)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("New User Text", state.text)
    }

    @Test
    fun `recoveryAfterSavingReplacementPhoto - restores newer photo and removes previous`() {
        val storage = TestablePhotoStorage()

        viewModel.selectPhoto(storage, "photo-original")
        testDispatcher.scheduler.advanceUntilIdle()
        val photo1 = viewModel.uiState.value.originalPhoto

        viewModel.selectPhoto(storage, "photo-replacement")
        testDispatcher.scheduler.advanceUntilIdle()
        val photo2 = viewModel.uiState.value.originalPhoto

        assertNotNull(photo1)
        assertNotNull(photo2)
        assertTrue(photo1 != photo2)
        assertEquals("file-photo-replacement.original", photo2?.privateFileName)
        assertTrue(storage.removedPhotos.contains(photo1))
    }

    @Test
    fun `previousPhotoCleanupFailure - does not delete newly adopted photo or report error`() {
        val storage = TestablePhotoStorage(throwOnRemovePhotoName = "file-photo-1.original")

        viewModel.selectPhoto(storage, "photo-1")
        testDispatcher.scheduler.advanceUntilIdle()
        val photo1 = viewModel.uiState.value.originalPhoto
        assertNotNull(photo1)

        // Select replacement photo-2; removal of photo-1 will throw an IOException
        viewModel.selectPhoto(storage, "photo-2")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull("Newly adopted photo must remain set in UI state", state.originalPhoto)
        assertEquals("file-photo-2.original", state.originalPhoto?.privateFileName)
        assertNull("Photo error must not be reported when adoption succeeded", state.photoError)
        assertEquals("file-photo-2.original", storage.savedRecords["sender-uid"]?.photo?.privateFileName)
    }

    @Test
    fun `accountSwitchAndReturn - preserves original journal and photo consistency`() {
        val storage = TestablePhotoStorage()
        viewModel.configurePhotos(storage)

        viewModel.selectPhoto(storage, "photo-account-a")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateText("Text from Account A")
        testDispatcher.scheduler.advanceUntilIdle()

        val photoA = viewModel.uiState.value.originalPhoto
        assertNotNull(photoA)

        // Switch to Account B
        mockAuthAdapter.setUid("account-b")
        sessionTracker.startSession("account-b")
        viewModel.syncSessionState()

        val stateB = viewModel.uiState.value
        assertEquals("account-b", stateB.activeSessionUid)
        assertNull(stateB.originalPhoto)

        // Switch back to Account A
        mockAuthAdapter.setUid("sender-uid")
        sessionTracker.startSession("sender-uid")
        viewModel.syncSessionState()
        testDispatcher.scheduler.advanceUntilIdle()

        val stateA = viewModel.uiState.value
        assertEquals("sender-uid", stateA.activeSessionUid)
        assertEquals("Text from Account A", stateA.text)
        assertNotNull(stateA.originalPhoto)
        assertEquals(photoA, stateA.originalPhoto)
    }

    @Test
    fun `failedRollback - does not delete retained bytes while journal reference uncertain`() {
        val storage = TestablePhotoStorage(
            failSavePredicate = { _, draft -> draft.photo == null }
        )

        // Select photo-1; initial save succeeds
        viewModel.selectPhoto(storage, "photo-1")
        testDispatcher.scheduler.advanceUntilIdle()

        val photo1 = viewModel.uiState.value.originalPhoto
        assertNotNull(photo1)
        assertEquals("file-photo-1.original", storage.savedRecords["sender-uid"]?.photo?.privateFileName)

        // Now removePhoto() triggers persistPhotoDraft(previousPhoto), setting photo=null
        // failSavePredicate throws IOException during rollback save!
        viewModel.removePhoto()
        testDispatcher.scheduler.advanceUntilIdle()

        // Rollback save failed! Verify that file-photo-1.original was NOT deleted from storage.removedPhotos
        assertFalse("Retained bytes must NOT be deleted while journal reference remains uncertain",
            storage.removedPhotos.any { it.privateFileName == "file-photo-1.original" })
        assertEquals("file-photo-1.original", storage.savedRecords["sender-uid"]?.photo?.privateFileName)
    }

    @Test
    fun `interruptedInitialSave - cleans up retained candidate photo and does not update journal`() {
        val storage = TestablePhotoStorage(
            failSavePredicate = { _, draft -> draft.photo != null }
        )

        viewModel.selectPhoto(storage, "photo-failed-initial")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.originalPhoto)
        assertNull(storage.savedRecords["sender-uid"]?.photo)
        assertTrue(storage.removedPhotos.any { it.privateFileName == "file-photo-failed-initial.original" })
    }

    @Test
    fun `commitToJournalFollowedByIOException_preservesJournalRecordAndRetainedPhoto`() {
        val storage = TestablePhotoStorage(
            postSaveException = IOException("Simulated post-commit I/O failure")
        )

        viewModel.selectPhoto(storage, "post-commit-io-uri")
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify that the error message was set
        assertEquals(R.string.photo_io_failure, viewModel.uiState.value.photoError)

        // Verify that the journal still contains the record written before the exception
        val saved = storage.savedRecords["sender-uid"]
        assertNotNull(saved)
        assertEquals("file-post-commit-io-uri.original", saved?.photo?.privateFileName)

        // Verify that candidate bytes were NOT removed
        assertFalse(storage.removedPhotos.any { it.privateFileName == "file-post-commit-io-uri.original" })
    }

    @Test
    fun `commitToJournalFollowedByCancellation_entersCancellationHandlerAndPreservesPhoto`() {
        val storage = TestablePhotoStorage(
            postSaveException = CancellationException("Simulated post-commit cancellation")
        )

        viewModel.selectPhoto(storage, "post-commit-cancel-uri")
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify that the cancellation handler was genuinely entered by checking restoreCount
        assertTrue("Cancellation handler must enter restore() to check journal state", storage.restoreCount > 0)

        // Verify that the journal contains the record
        val saved = storage.savedRecords["sender-uid"]
        assertNotNull(saved)
        assertEquals("file-post-commit-cancel-uri.original", saved?.photo?.privateFileName)

        // Verify that the retained photo was NOT deleted
        assertFalse(storage.removedPhotos.any { it.privateFileName == "file-post-commit-cancel-uri.original" })
    }

    @Test
    fun `failedJournalReadDuringCleanupSafetyCheck_failsClosedAndPreservesPhoto`() {
        val storage = TestablePhotoStorage(
            failSavePredicate = { _, draft -> draft.photo != null },
            failRestore = true
        )

        viewModel.selectPhoto(storage, "failed-read-uri")
        testDispatcher.scheduler.advanceUntilIdle()

        // On restore failure, selectPhoto fails closed and does NOT delete candidate bytes
        assertFalse(storage.removedPhotos.any { it.privateFileName == "file-failed-read-uri.original" })
    }

    @Test
    fun `obsoleteOperationDuringSave_doesNotOverwriteNewerDraft`() {
        val saveGate1 = CompletableDeferred<Unit>()
        val sharedStorage = TestablePhotoStorage(
            saveGatePredicate = { draft ->
                if (draft.photo?.privateFileName == "file-photo-1.original") saveGate1 else null
            }
        )

        // Photo 1 starts saving on sharedStorage and suspends on saveGate1
        viewModel.selectPhoto(sharedStorage, "photo-1")
        testDispatcher.scheduler.runCurrent()

        // Photo 2 is selected on the SAME sharedStorage and completes immediately
        viewModel.selectPhoto(sharedStorage, "photo-2")
        testDispatcher.scheduler.runCurrent()

        // Now photo 1's save finishes
        saveGate1.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify that photo 1 did NOT overwrite photo 2 in UI or in the shared storage journal
        val state = viewModel.uiState.value
        assertEquals("file-photo-2.original", state.originalPhoto?.privateFileName)
        assertEquals("file-photo-2.original", sharedStorage.savedRecords["sender-uid"]?.photo?.privateFileName)

        // Verify photo 1 was cleaned up and photo 2 was preserved in sharedStorage
        assertTrue(sharedStorage.removedPhotos.any { it.privateFileName == "file-photo-1.original" })
        assertFalse(sharedStorage.removedPhotos.any { it.privateFileName == "file-photo-2.original" })
    }
}

// --- Fakes for Test Isolation ---

/** Testable photo storage implementation for coroutine race assertions. */
private class TestablePhotoStorage(
    val retainGate: CompletableDeferred<Unit>? = null,
    val saveGate: CompletableDeferred<Unit>? = null,
    val saveGatePredicate: ((draft: PhotoDraftRecord) -> CompletableDeferred<Unit>?)? = null,
    val restoreGate: CompletableDeferred<PhotoDraftRecord?>? = null,
    val throwOnRemovePhotoName: String? = null,
    val failSavePredicate: ((uid: String, draft: PhotoDraftRecord) -> Boolean)? = null,
    var postSaveException: Exception? = null,
    var failRestore: Boolean = false,
    var onAfterSave: (() -> Unit)? = null
) : PhotoDraftStorage {
    val removedPhotos = mutableListOf<RetainedOriginalPhoto>()
    val savedRecords = mutableMapOf<String, PhotoDraftRecord>()
    var restoreCount = 0

    override suspend fun retain(session: StorageSession, uid: String, capsuleId: String, uri: String): RetainedOriginalPhoto {
        retainGate?.await()
        return RetainedOriginalPhoto(uid, capsuleId, "file-$uri.original", OriginalPhotoFormat.JPEG, 1024L, "hash-$uri")
    }

    override fun file(photo: RetainedOriginalPhoto): File = File("/private/${photo.privateFileName}")

    override suspend fun remove(photo: RetainedOriginalPhoto) {
        if (throwOnRemovePhotoName != null && photo.privateFileName == throwOnRemovePhotoName) {
            throw IOException("Simulated cleanup failure for previous photo")
        }
        removedPhotos.add(photo)
    }

    override suspend fun save(session: StorageSession, uid: String, draft: PhotoDraftRecord) {
        saveGate?.await()
        saveGatePredicate?.invoke(draft)?.await()
        if (failSavePredicate?.invoke(uid, draft) == true) {
            throw IOException("Simulated save failure")
        }
        savedRecords[uid] = draft
        onAfterSave?.invoke()
        postSaveException?.let { throw it }
    }

    override suspend fun restore(uid: String): PhotoDraftRecord? {
        restoreCount++
        if (failRestore) throw IOException("Simulated restore failure")
        val gate = restoreGate
        return if (gate != null) gate.await() else savedRecords[uid]
    }
}

// --- Fakes for Test Isolation ---

/** Emulates validated input without requiring Android's URI implementation. */
private class FakePhotoStorage(private val size: Long) : PhotoDraftStorage {
    /** Rejects invalid fixtures or returns retained metadata for the requested identity. */
    override suspend fun retain(session: StorageSession, uid: String, capsuleId: String, uri: String): RetainedOriginalPhoto {
        if (size !in 1L..5_242_880L) throw InvalidOriginalPhotoException("Invalid size")
        return RetainedOriginalPhoto(uid, capsuleId, "fixture.original", OriginalPhotoFormat.JPEG,
            size, "fixture-hash")
    }
    /** Supplies a stable private path for mapping assertions. */
    override fun file(photo: RetainedOriginalPhoto): File = File("/private/photo.jpg")
    /** This fixture owns no actual files. */
    override suspend fun remove(photo: RetainedOriginalPhoto) {}
    /** Persistence has separate real-filesystem tests. */
    override suspend fun save(session: StorageSession, uid: String, draft: PhotoDraftRecord) {}
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
