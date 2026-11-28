package com.example.timecapsule.ui.draft

import com.example.timecapsule.data.session.StorageSession
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timecapsule.R
import com.example.timecapsule.data.media.InvalidOriginalPhotoException
import com.example.timecapsule.data.media.PhotoDraftStorage
import com.example.timecapsule.data.media.PhotoDraftRecord
import com.example.timecapsule.domain.model.RetainedOriginalPhoto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import com.example.timecapsule.data.AccountSessionManager
import com.example.timecapsule.data.CapsuleRepository
import com.example.timecapsule.data.recovery.ForegroundRecoveryCoordinator
import com.example.timecapsule.domain.model.PublicationPhase
import com.example.timecapsule.domain.model.RecoveredPublication
import com.example.timecapsule.domain.model.CapsuleDraft
import com.example.timecapsule.domain.model.CapsuleLock
import com.example.timecapsule.domain.model.LocationTarget
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.domain.model.RecipientConfirmation
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId
import com.example.timecapsule.domain.result.PublicationFailure

/** Recipient target mode selection for S-05 recipient step. */
enum class RecipientMode {
    FUTURE_ME,
    SOMEONE_ELSE
}

/** Status of exact recipient UID directory lookup. */
enum class RecipientLookupStatus {
    IDLE,
    LOOKING_UP,
    FOUND,
    NONEXISTENT,
    ERROR
}

/**
 * UI State data class capturing the entire draft and review creation flow across S-04, S-05, and S-06.
 */
data class CapsuleDraftUiState(
    val capsuleId: String = UUID.randomUUID().toString(),
    val title: String = "",
    val text: String = "",
    val localPhotoUri: String? = null,
    val photoName: String? = null,
    val photoSizeBytes: Long? = null,
    val originalPhoto: RetainedOriginalPhoto? = null,
    val photoBusy: Boolean = false,
    val photoError: Int? = null,
    val recipientMode: RecipientMode = RecipientMode.FUTURE_ME,
    val recipientUidInput: String = "",
    val lookupStatus: RecipientLookupStatus = RecipientLookupStatus.IDLE,
    val lookupErrorMessage: String? = null,
    val resolvedProfile: RecipientProfile? = null,
    val isRecipientConfirmed: Boolean = false,
    val lockMode: LockMode = LockMode.TIME,
    val unlockAt: Instant? = null,
    val creationZone: String = ZoneId.systemDefault().id,
    val locationTarget: LocationTarget? = null,
    val locationName: String? = null,
    val activeSessionUid: String? = null,
    val publicationPhase: PublicationPhase? = null,
    val publicationBusy: Boolean = false,
    val recoveryBusy: Boolean = false,
    val recoveryBlocked: Boolean = false,
    val statusOnly: Boolean = false,
    val canAbandon: Boolean = false,
    val publicationFailure: PublicationFailure? = null,
    val canRetryUnchanged: Boolean = false,
    val publicationMessage: Int? = null
) {
    /** Frozen, completed and unresolved submissions never become editable through Back navigation. */
    val editable: Boolean
        get() = !statusOnly && publicationPhase == null && !publicationBusy && !recoveryBusy && !recoveryBlocked
}

/**
 * ViewModel managing the shared draft state for S-04 (Content), S-05 (Recipient & Locks), and S-06 (Review).
 * Preserves draft state across navigation, enforces recipient lookup and explicit confirmation,
 * rejects stale asynchronous lookup callbacks, and invalidates state on account/session changes.
 */
class CapsuleDraftViewModel(
    private val accountSessionManager: AccountSessionManager,
    private var repository: CapsuleRepository? = null,
    private var recoveryCoordinator: ForegroundRecoveryCoordinator? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(CapsuleDraftUiState())
    val uiState: StateFlow<CapsuleDraftUiState> = _uiState.asStateFlow()

    private var activeLookupToken: Long = 0L
    private var photoToken = 0L
    private var photoInput: PhotoDraftStorage? = null
    private val photoSaveMutex = Mutex()
    private var publicationToken = 0L
    private var requestedId: String? = null
    private var requestedSession: SessionSnapshot? = null
    private var observedSession = accountSessionManager.currentSession()

    /** Configures account-private persistence once for this activity-scoped ViewModel. */
    fun configurePhotos(input: PhotoDraftStorage, restore: Boolean = true) {
        if (photoInput != null) return
        photoInput = input
        if (restore) recoverPublicationState(restoreEditable = true)
    }

    /** Re-evaluates whether a pending photo operation matches current token, session, and capsule ID. */
    private fun isCurrentPhotoOp(token: Long, uid: String, capsuleId: String): Boolean {
        val state = _uiState.value
        return token == photoToken &&
            state.activeSessionUid == uid &&
            accountSessionManager.currentSession().uid == uid &&
            accountSessionManager.currentSession() == observedSession &&
            state.capsuleId == capsuleId
    }

    /** Restores only the still-active account; recipient confirmation must be established again. */
    private fun restorePhotoDraft() {
        val input = photoInput ?: return
        val uid = _uiState.value.activeSessionUid ?: return
        val capsuleId = _uiState.value.capsuleId
        val token = ++photoToken
        _uiState.update { it.copy(photoBusy = true) }
        viewModelScope.launch {
            try {
                val record = input.restore(uid)
                if (!isCurrentPhotoOp(token, uid, capsuleId)) return@launch
                _uiState.update { state ->
                    if (record == null) {
                        state.copy(photoBusy = false)
                    } else {
                        state.copy(
                            capsuleId = record.capsuleId,
                            title = if (state.title.isBlank()) record.title else state.title,
                            text = if (state.text.isBlank()) record.text else state.text,
                            originalPhoto = record.photo,
                            localPhotoUri = record.photo?.let { input.file(it).toURI().toString() },
                            photoName = record.photo?.let { "Photo" },
                            photoSizeBytes = record.photo?.sizeBytes,
                            locationTarget = record.locationTarget ?: state.locationTarget,
                            locationName = record.locationName ?: state.locationName,
                            photoBusy = false
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                photoFailed(token, R.string.photo_restore_failure)
            } catch (error: IllegalArgumentException) {
                photoFailed(token, R.string.photo_restore_failure)
            }
        }
    }

    /** Captures editable fields now and queues their atomic save in UI event order. */
    private fun persistPhotoDraft(released: RetainedOriginalPhoto? = null) {
        val input = photoInput ?: return
        val state = _uiState.value
        val uid = state.activeSessionUid ?: return
        val storageSession = StorageSession(observedSession, accountSessionManager::currentSession)
        val capsuleId = state.capsuleId
        val record = PhotoDraftRecord(capsuleId, state.title, state.text, state.originalPhoto, state.locationTarget, state.locationName)
        viewModelScope.launch {
            try {
                photoSaveMutex.withLock {
                    if (_uiState.value.activeSessionUid != uid ||
                        accountSessionManager.currentSession().uid != uid ||
                        accountSessionManager.currentSession() != observedSession ||
                        _uiState.value.capsuleId != capsuleId) {
                        return@withLock
                    }
                    input.save(storageSession, uid, record)
                    if (released != null && released != record.photo) {
                        try {
                            input.remove(released)
                        } catch (_: Exception) {
                            // Cleanup failure of released photo does not invalidate saved draft
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                if (_uiState.value.activeSessionUid == uid) {
                    _uiState.update { it.copy(photoError = R.string.photo_save_failure) }
                }
            }
        }
    }

    /** Retains provider content asynchronously and ignores obsolete selections or accounts. */
    fun selectPhoto(input: PhotoDraftStorage, uri: String) {
        if (!_uiState.value.editable) return
        val state = _uiState.value
        val uid = state.activeSessionUid ?: return
        val storageSession = StorageSession(observedSession, accountSessionManager::currentSession)
        val capsuleId = state.capsuleId
        val token = ++photoToken
        _uiState.update { it.copy(photoBusy = true, photoError = null) }
        viewModelScope.launch {
            var retainedPhoto: RetainedOriginalPhoto? = null
            try {
                val photo = input.retain(storageSession, uid, capsuleId, uri)
                retainedPhoto = photo
                if (!isCurrentPhotoOp(token, uid, capsuleId)) {
                    input.remove(photo)
                    retainedPhoto = null
                    return@launch
                }
                var saveSuccess = false
                var previousPhoto: RetainedOriginalPhoto? = null
                photoSaveMutex.withLock {
                    if (!isCurrentPhotoOp(token, uid, capsuleId)) {
                        return@withLock
                    }
                    val latestState = _uiState.value
                    previousPhoto = latestState.originalPhoto
                    val record = PhotoDraftRecord(capsuleId, latestState.title, latestState.text, photo, latestState.locationTarget, latestState.locationName)
                    try {
                        input.save(storageSession, uid, record)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        return@withLock
                    }
                    if (isCurrentPhotoOp(token, uid, capsuleId)) {
                        saveSuccess = true
                    } else {
                        // Operation became obsolete while saving to journal (e.g. account switch or discard during save).
                        // Reconcile journal to previous state so journal does not reference the photo being removed.
                        // Only roll back if journal still references this photo to avoid overwriting a newer draft.
                        try {
                            val currentInJournal = input.restore(uid)
                            if (currentInJournal?.capsuleId == capsuleId && currentInJournal.photo == photo) {
                                input.save(storageSession, uid, PhotoDraftRecord(capsuleId, latestState.title, latestState.text, previousPhoto, latestState.locationTarget, latestState.locationName))
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // Rollback failed
                        }
                    }
                }
                if (!saveSuccess || !isCurrentPhotoOp(token, uid, capsuleId)) {
                    val removePhoto = try {
                        val currentInJournal = input.restore(uid)
                        currentInJournal?.photo != photo
                    } catch (_: Exception) {
                        // On journal read failure, fail closed and preserve bytes to prevent dangling reference.
                        false
                    }
                    if (removePhoto) {
                        try { input.remove(photo) } catch (_: Exception) {}
                    }
                    retainedPhoto = null
                    photoFailed(token, R.string.photo_io_failure)
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        originalPhoto = photo,
                        localPhotoUri = input.file(photo).toURI().toString(),
                        photoName = "Photo",
                        photoSizeBytes = photo.sizeBytes,
                        photoBusy = false
                    )
                }
                // Photo is now durably adopted in UI state and journal; clear candidate reference so error handlers won't delete it
                retainedPhoto = null

                if (previousPhoto != null && previousPhoto != photo) {
                    try {
                        input.remove(previousPhoto)
                    } catch (_: Exception) {
                        // Previous photo cleanup failure does not invalidate the newly adopted photo
                    }
                }
            } catch (cancelled: CancellationException) {
                retainedPhoto?.let { p ->
                    try {
                        val currentInJournal = input.restore(uid)
                        if (currentInJournal?.photo != p) {
                            try { input.remove(p) } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {
                        // On journal read error, preserve photo bytes to prevent dangling reference.
                    }
                }
                throw cancelled
            } catch (error: SecurityException) {
                retainedPhoto?.let { p ->
                    try {
                        val currentInJournal = input.restore(uid)
                        if (currentInJournal?.photo != p) {
                            try { input.remove(p) } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {
                        // On journal read error, preserve photo bytes to prevent dangling reference.
                    }
                }
                photoFailed(token, R.string.photo_access_denied)
            } catch (error: InvalidOriginalPhotoException) {
                retainedPhoto?.let { p ->
                    try {
                        val currentInJournal = input.restore(uid)
                        if (currentInJournal?.photo != p) {
                            try { input.remove(p) } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {
                        // On journal read error, preserve photo bytes to prevent dangling reference.
                    }
                }
                photoFailed(token, R.string.photo_invalid)
            } catch (error: IOException) {
                retainedPhoto?.let { p ->
                    try {
                        val currentInJournal = input.restore(uid)
                        if (currentInJournal?.photo != p) {
                            try { input.remove(p) } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {
                        // On journal read error, preserve photo bytes to prevent dangling reference.
                    }
                }
                photoFailed(token, R.string.photo_io_failure)
            }
        }
    }

    /** Updates only the current request's error without replacing a previous valid selection. */
    private fun photoFailed(token: Long, message: Int) {
        if (token == photoToken) _uiState.update { it.copy(photoBusy = false, photoError = message) }
    }

    init {
        val currentUid = accountSessionManager.currentSession().uid
        observedSession = accountSessionManager.currentSession()
        _uiState.update { it.copy(activeSessionUid = currentUid) }
        if (!currentUid.isNullOrBlank()) {
            selectFutureMe(currentUid)
        }
        viewModelScope.launch {
            accountSessionManager.sessionTracker.observeSession().collect { session ->
                val id = requestedId
                if (id != null && session == accountSessionManager.currentSession() && session != observedSession) {
                    resetSession(session, id)
                }
            }
        }
    }

    /**
     * Re-evaluates session state to prevent cross-session leakage.
     * Clears draft or recipient confirmation if user session has changed.
     */
    fun syncSessionState(
        latestRepository: CapsuleRepository? = repository,
        latestRecoveryCoordinator: ForegroundRecoveryCoordinator? = recoveryCoordinator
    ) {
        if (latestRepository != null) this.repository = latestRepository
        if (latestRecoveryCoordinator != null) this.recoveryCoordinator = latestRecoveryCoordinator

        val leavingTarget = requestedId != null
        requestedId = null
        requestedSession = null
        val current = accountSessionManager.currentSession()
        if (current != observedSession || leavingTarget) {
            resetSession(current)
            if (current.isAuthenticated) recoverPublicationState(restoreEditable = true)
        } else if (!_uiState.value.publicationBusy && !_uiState.value.recoveryBusy) {
            recoverPublicationState()
        }

        viewModelScope.launch {
            accountSessionManager.restoreSessionIfAuthenticated()
            val restored = accountSessionManager.currentSession()
            if (requestedId == null && restored != observedSession) {
                resetSession(restored)
                if (restored.isAuthenticated) recoverPublicationState(restoreEditable = true)
            }
        }
    }

    /** Invalidate every pending callback before installing a clean state for this generation. */
    private fun resetSession(session: SessionSnapshot, target: String? = null) {
        observedSession = session
        photoToken++
        publicationToken++
        activeLookupToken++
        if (target == null) {
            _uiState.value = CapsuleDraftUiState(activeSessionUid = session.uid)
            session.uid?.let { selectFutureMe(it) }
        } else {
            _uiState.value = CapsuleDraftUiState(activeSessionUid = session.uid, capsuleId = target,
                recoveryBlocked = true, statusOnly = true,
                publicationMessage = R.string.publication_recovery_failure)
        }
    }

    /** Updates optional capsule title. */
    fun updateTitle(newTitle: String) {
        if (!_uiState.value.editable) return
        _uiState.update { it.copy(title = newTitle) }
        persistPhotoDraft()
    }

    /** Updates message body text. */
    fun updateText(newText: String) {
        if (!_uiState.value.editable) return
        _uiState.update { it.copy(text = newText) }
        persistPhotoDraft()
    }

    /** Removes attached photo. */
    fun removePhoto() {
        if (!_uiState.value.editable) return
        val previous = _uiState.value.originalPhoto
        photoToken++
        _uiState.update {
            it.copy(
                localPhotoUri = null,
                photoName = null,
                photoSizeBytes = null,
                originalPhoto = null, photoBusy = false, photoError = null
            )
        }
        persistPhotoDraft(previous)
    }

    /** Selects recipient mode: Future Me or Someone Else. */
    fun setRecipientMode(mode: RecipientMode) {
        if (!_uiState.value.editable) return
        activeLookupToken++
        _uiState.update {
            it.copy(
                recipientMode = mode,
                isRecipientConfirmed = false,
                lookupStatus = RecipientLookupStatus.IDLE,
                lookupErrorMessage = null
            )
        }
        if (mode == RecipientMode.FUTURE_ME) {
            val currentUid = accountSessionManager.currentSession().uid ?: _uiState.value.activeSessionUid ?: ""
            selectFutureMe(currentUid)
        } else {
            _uiState.update {
                it.copy(
                    recipientUidInput = "",
                    resolvedProfile = null
                )
            }
        }
    }

    /** Sets self as recipient ("Future Me"). */
    fun selectFutureMe(selfUid: String) {
        if (!_uiState.value.editable) return
        val cleanUid = selfUid.trim()
        if (cleanUid.isBlank()) return
        val selfProfile = RecipientProfile(uid = cleanUid, displayName = "Future Me ($cleanUid)")
        _uiState.update {
            it.copy(
                recipientMode = RecipientMode.FUTURE_ME,
                recipientUidInput = cleanUid,
                resolvedProfile = selfProfile,
                lookupStatus = RecipientLookupStatus.FOUND,
                isRecipientConfirmed = true,
                lookupErrorMessage = null
            )
        }
    }

    /**
     * Updates UID input field.
     * CRITICAL: Resets confirmation, clears resolved profile, and increments lookup token if input text changes.
     */
    fun updateRecipientUidInput(newUidText: String) {
        if (!_uiState.value.editable) return
        val trimmed = newUidText.trim()
        val previousInput = _uiState.value.recipientUidInput.trim()
        val textChanged = trimmed != previousInput

        if (textChanged) {
            activeLookupToken++
        }

        _uiState.update {
            it.copy(
                recipientUidInput = newUidText,
                isRecipientConfirmed = if (textChanged) false else it.isRecipientConfirmed,
                resolvedProfile = if (textChanged) null else it.resolvedProfile,
                lookupStatus = if (textChanged) RecipientLookupStatus.IDLE else it.lookupStatus,
                lookupErrorMessage = if (textChanged) null else it.lookupErrorMessage
            )
        }
    }

    /**
     * Performs exact recipient directory lookup via [accountSessionManager].
     * Increments activeLookupToken to invalidate stale asynchronous responses.
     */
    fun performRecipientLookup() {
        if (!_uiState.value.editable) return
        val rawInput = _uiState.value.recipientUidInput
        val cleanUid = rawInput.trim()
        if (cleanUid.isBlank()) {
            _uiState.update {
                it.copy(
                    lookupStatus = RecipientLookupStatus.ERROR,
                    lookupErrorMessage = "Recipient UID must not be blank.",
                    resolvedProfile = null,
                    isRecipientConfirmed = false
                )
            }
            return
        }

        val currentToken = ++activeLookupToken
        _uiState.update {
            it.copy(
                lookupStatus = RecipientLookupStatus.LOOKING_UP,
                lookupErrorMessage = null,
                isRecipientConfirmed = false
            )
        }

        viewModelScope.launch {
            val result = accountSessionManager.lookupRecipient(cleanUid)

            // Reject stale callback if user edited UID input while lookup was in flight
            if (currentToken != activeLookupToken || _uiState.value.recipientUidInput.trim() != cleanUid) {
                return@launch
            }

            when (result) {
                is OperationResult.Success -> {
                    _uiState.update {
                        it.copy(
                            lookupStatus = RecipientLookupStatus.FOUND,
                            resolvedProfile = result.data.profile,
                            isRecipientConfirmed = false,
                            lookupErrorMessage = null
                        )
                    }
                }
                is OperationResult.Invalid -> {
                    _uiState.update {
                        it.copy(
                            lookupStatus = RecipientLookupStatus.NONEXISTENT,
                            resolvedProfile = null,
                            isRecipientConfirmed = false,
                            lookupErrorMessage = result.message
                        )
                    }
                }
                is OperationResult.Unavailable -> {
                    _uiState.update {
                        it.copy(
                            lookupStatus = RecipientLookupStatus.ERROR,
                            resolvedProfile = null,
                            isRecipientConfirmed = false,
                            lookupErrorMessage = result.message
                        )
                    }
                }
                is OperationResult.Denied -> {
                    _uiState.update {
                        it.copy(
                            lookupStatus = RecipientLookupStatus.ERROR,
                            resolvedProfile = null,
                            isRecipientConfirmed = false,
                            lookupErrorMessage = result.message
                        )
                    }
                }
                is OperationResult.OutcomeUnknown -> {
                    _uiState.update {
                        it.copy(
                            lookupStatus = RecipientLookupStatus.ERROR,
                            resolvedProfile = null,
                            isRecipientConfirmed = false,
                            lookupErrorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    /**
     * Confirms the currently resolved recipient profile via [accountSessionManager].
     */
    fun confirmRecipient() {
        if (!_uiState.value.editable) return
        val profile = _uiState.value.resolvedProfile
        if (profile != null) {
            val unconfirmed = RecipientConfirmation(profile, false)
            val confirmed = accountSessionManager.confirmRecipient(unconfirmed, true)
            _uiState.update {
                it.copy(
                    isRecipientConfirmed = confirmed.isConfirmed,
                    lookupErrorMessage = null
                )
            }
        }
    }

    /** Updates lock mode: TIME, LOCATION, or TIME_AND_LOCATION. */
    fun updateLockMode(mode: LockMode) {
        if (!_uiState.value.editable) return
        _uiState.update { it.copy(lockMode = mode) }
    }

    /** Updates target unlock date/time instant and optional creation timezone. */
    fun updateUnlockAt(instant: Instant?, zoneId: String = ZoneId.systemDefault().id) {
        if (!_uiState.value.editable) return
        _uiState.update { it.copy(unlockAt = instant, creationZone = zoneId) }
    }

    /** Updates target location coordinates and location name. */
    fun updateLocation(location: LocationTarget?, name: String?) {
        if (!_uiState.value.editable) return
        _uiState.update {
            it.copy(
                locationTarget = location,
                locationName = name
            )
        }
        persistPhotoDraft()
    }

    /** Validates S-04 content requirements: message body or photo required. Title alone is invalid. */
    fun validateS04Content(): OperationResult<Unit> {
        val state = _uiState.value
        if (state.recoveryBusy || state.recoveryBlocked) {
            return OperationResult.Unavailable("Recover the saved send before continuing.")
        }
        if (state.publicationPhase != null) return OperationResult.Success(Unit)
        if (state.photoBusy) return OperationResult.Invalid("Please wait for the photo to finish.")
        val hasText = state.text.isNotBlank()
        val hasPhoto = state.originalPhoto != null
        return if (hasText || hasPhoto) {
            OperationResult.Success(Unit)
        } else {
            OperationResult.Invalid("Please enter a message or attach a photo for your capsule.")
        }
    }

    /** Validates S-05 recipient and lock requirements before proceeding to S-06 review. */
    fun validateS05RecipientAndLocks(): OperationResult<Unit> {
        val contentRes = validateS04Content()
        if (contentRes !is OperationResult.Success) return contentRes
        if (_uiState.value.publicationPhase != null) return OperationResult.Success(Unit)

        val state = _uiState.value
        if (state.recipientUidInput.trim().isBlank()) {
            return OperationResult.Invalid("Please enter or select a recipient UID.")
        }
        if (!state.isRecipientConfirmed || state.resolvedProfile == null) {
            return OperationResult.Invalid("Please confirm the recipient before proceeding.")
        }

        when (state.lockMode) {
            LockMode.TIME -> {
                if (state.unlockAt == null || !state.unlockAt.isAfter(Instant.now())) {
                    return OperationResult.Invalid("Please select a valid future unlock date & time.")
                }
            }
            LockMode.LOCATION -> {
                if (state.locationTarget == null) {
                    return OperationResult.Invalid("Please select a target location for the capsule.")
                }
            }
            LockMode.TIME_AND_LOCATION -> {
                if (state.unlockAt == null || !state.unlockAt.isAfter(Instant.now())) {
                    return OperationResult.Invalid("Please select a valid future unlock date & time.")
                }
                if (state.locationTarget == null) {
                    return OperationResult.Invalid("Please select a target location for the capsule.")
                }
            }
        }
        return OperationResult.Success(Unit)
    }

    /** Builds the immutable domain [CapsuleDraft] snapshot. */
    fun buildCapsuleDraft(): CapsuleDraft {
        val state = _uiState.value
        val lock = CapsuleLock(
            mode = state.lockMode,
            unlockAt = state.unlockAt,
            location = state.locationTarget
        )
        return CapsuleDraft(
            capsuleId = state.capsuleId,
            recipientUid = state.resolvedProfile?.uid ?: state.recipientUidInput.trim(),
            text = state.text,
            localPhotoUri = state.localPhotoUri,
            lock = lock,
            title = state.title.ifBlank { null },
            recipientDisplayName = state.resolvedProfile?.displayName,
            isRecipientConfirmed = state.isRecipientConfirmed,
            locationName = state.locationName,
            originalPhoto = state.originalPhoto,
            creationZone = state.creationZone
        )
    }

    /** Reconciles saved sends before restoring raw compose data, particularly for locked self-sends. */
    fun recoverPublicationState(
        targetCapsuleId: String? = null,
        restoreEditable: Boolean = false,
        origin: SessionSnapshot? = null
    ) {
        val currentSession = accountSessionManager.currentSession()
        if (targetCapsuleId != null) {
            val selectionSession = origin ?: requestedSession?.takeIf { requestedId == targetCapsuleId } ?: currentSession
            val sameTarget = requestedId == targetCapsuleId && requestedSession == selectionSession
            if (sameTarget && selectionSession == currentSession &&
                (_uiState.value.publicationBusy || _uiState.value.recoveryBusy)) return
            requestedId = targetCapsuleId
            requestedSession = selectionSession
            resetSession(currentSession, targetCapsuleId)
            if (selectionSession != currentSession || targetCapsuleId.isBlank()) return
        } else {
            if (requestedId != null) return
            if (observedSession != currentSession) resetSession(currentSession)
        }
        val backend = repository
        if (backend == null) {
            if (targetCapsuleId == null && restoreEditable) restorePhotoDraft()
            return
        }
        val uid = currentSession.uid ?: return
        val state = _uiState.value
        if (state.publicationBusy || state.recoveryBusy) return
        val token = ++publicationToken
        _uiState.update { it.copy(recoveryBusy = true) }
        viewModelScope.launch {
            try {
                photoSaveMutex.withLock { /* Drain earlier compose saves before final cleanup. */ }
                if (!isCurrentPublication(uid, token, currentSession)) return@launch
                val result = recoverSaved(backend, currentSession)
                if (!isCurrentPublication(uid, token, currentSession)) return@launch
                if (result is OperationResult.Success) {
                    val recovered = if (!targetCapsuleId.isNullOrBlank()) {
                        result.data.firstOrNull { it.capsuleId == targetCapsuleId &&
                            (it.snapshot == null || (it.snapshot.capsuleId == targetCapsuleId && it.snapshot.senderUid == uid)) }
                    } else {
                        val pending = result.data.firstOrNull { it.phase != PublicationPhase.SENT }
                        val completed = result.data.firstOrNull {
                            it.capsuleId == _uiState.value.capsuleId || it.clearedEditableDraft
                        }
                        pending ?: completed
                    }
                    if (recovered != null) {
                        applyRecovered(recovered)
                    } else if (!targetCapsuleId.isNullOrBlank()) {
                        _uiState.update { it.copy(
                            capsuleId = targetCapsuleId,
                            recoveryBusy = false,
                            recoveryBlocked = true,
                            statusOnly = true,
                            publicationPhase = PublicationPhase.FAILED,
                            publicationMessage = R.string.publication_recovery_failure
                        ) }
                    } else {
                        _uiState.update { it.copy(recoveryBusy = false, recoveryBlocked = false) }
                        if (restoreEditable && _uiState.value.editable) restorePhotoDraft()
                    }
                } else {
                    _uiState.update { it.copy(recoveryBusy = false, recoveryBlocked = targetCapsuleId != null || !it.statusOnly,
                        publicationMessage = R.string.publication_recovery_failure) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (isCurrentPublication(uid, token, currentSession)) _uiState.update {
                    it.copy(recoveryBusy = false, recoveryBlocked = targetCapsuleId != null || !it.statusOnly,
                        publicationMessage = R.string.publication_recovery_failure)
                }
            }
        }
    }

    /** Renders a frozen operation without revalidating an elapsed unlock time or restoring SENT text. */
    private fun applyRecovered(recovered: RecoveredPublication) {
        val operation = recovered.snapshot
        photoToken++
        activeLookupToken++
        _uiState.update { state ->
            if (operation == null || operation.recipientUid == state.activeSessionUid || recovered.phase == PublicationPhase.SENT) {
                val messageRes = when (recovered.phase) {
                    PublicationPhase.SENT -> R.string.publication_sent
                    PublicationPhase.FAILED -> if (recovered.canAbandon) R.string.publication_failed else R.string.publication_unknown
                    else -> R.string.publication_unknown
                }
                state.copy(
                    statusOnly = true,
                    capsuleId = recovered.capsuleId,
                    text = "",
                    title = "",
                    localPhotoUri = null,
                    originalPhoto = null,
                    photoName = null,
                    photoSizeBytes = null,
                    photoBusy = false,
                    publicationPhase = recovered.phase,
                    canAbandon = recovered.canAbandon,
                    canRetryUnchanged = recovered.canRetryUnchanged,
                    publicationFailure = state.publicationFailure.takeIf { state.capsuleId == recovered.capsuleId && recovered.phase == PublicationPhase.FAILED },
                    publicationBusy = false,
                    recoveryBusy = false,
                    recoveryBlocked = false,
                    publicationMessage = messageRes
                )
            } else {
                state.copy(
                    statusOnly = false,
                    capsuleId = operation.capsuleId,
                    title = operation.title ?: "",
                    text = operation.text,
                    originalPhoto = operation.photo,
                    localPhotoUri = operation.photo?.let { photoInput?.file(it)?.toURI()?.toString() },
                    photoName = operation.photo?.let { "Photo" },
                    photoSizeBytes = operation.photo?.sizeBytes,
                    photoBusy = false,
                    recipientUidInput = operation.recipientUid,
                    resolvedProfile = if (operation.recipientUid.isNotBlank()) RecipientProfile(operation.recipientUid, operation.recipientUid) else state.resolvedProfile,
                    isRecipientConfirmed = operation.recipientUid.isNotBlank(),
                    lockMode = operation.lock.mode,
                    unlockAt = operation.lock.unlockAt,
                    creationZone = operation.creationZone,
                    locationTarget = operation.lock.location,
                    publicationPhase = recovered.phase,
                    canAbandon = recovered.canAbandon,
                    canRetryUnchanged = recovered.canRetryUnchanged,
                    publicationFailure = state.publicationFailure.takeIf { state.capsuleId == recovered.capsuleId && recovered.phase == PublicationPhase.FAILED },
                    publicationBusy = false,
                    recoveryBusy = false,
                    recoveryBlocked = false,
                    publicationMessage = if (recovered.phase == PublicationPhase.SENT) R.string.publication_sent
                        else if (recovered.canAbandon) R.string.publication_failed
                        else R.string.publication_unknown
                )
            }
        }
    }

    /** Freezes before any suspension; duplicate taps and all editing are rejected while submitting. */
    fun sendCapsule() {
        val backend = repository ?: return
        val state = _uiState.value
        val uid = state.activeSessionUid ?: return
        if (!state.editable || state.photoBusy || requestedId != null || observedSession != accountSessionManager.currentSession()) return
        if (validateS05RecipientAndLocks() !is OperationResult.Success) {
            _uiState.update { it.copy(publicationMessage = R.string.publication_invalid) }
            return
        }
        val draft = buildCapsuleDraft()
        val capturedSession = observedSession
        startPublicationAction(uid, capturedSession) { session -> backend.publish(draft, session) }
    }

    /** Replays only the coordinator's persisted snapshot, never fields edited in the UI. */
    fun retryPublication(targetId: String? = requestedId) {
        if (!matchesSelection(targetId)) return
        val backend = repository ?: return
        val state = _uiState.value
        val uid = state.activeSessionUid ?: return
        if (!state.canRetryUnchanged || state.publicationPhase == null || state.publicationPhase == PublicationPhase.SENT ||
            state.publicationBusy || state.recoveryBusy || state.recoveryBlocked) return
        val capturedSession = observedSession
        startPublicationAction(uid, capturedSession) { session -> backend.retryPublication(state.capsuleId, session) }
    }

    /** Waits for queued draft writes before publication; obsolete account results never repaint UI. */
    private fun startPublicationAction(
        uid: String,
        capturedSession: SessionSnapshot,
        action: suspend (SessionSnapshot) -> OperationResult<String>
    ) {
        val token = ++publicationToken
        photoToken++
        activeLookupToken++
        val before = _uiState.value
        val hideContent = before.statusOnly || (before.resolvedProfile?.uid ?: before.recipientUidInput.trim()) == uid
        _uiState.update { state ->
            val review = if (hideContent) state.copy(statusOnly = true, title = "", text = "",
                originalPhoto = null, localPhotoUri = null, photoName = null, photoSizeBytes = null) else state
            review.copy(publicationBusy = true, publicationPhase = PublicationPhase.PREPARED,
                canAbandon = false, canRetryUnchanged = false, publicationFailure = null, publicationMessage = R.string.publication_sending)
        }
        viewModelScope.launch {
            try {
                photoSaveMutex.withLock { /* Drain earlier compose saves before final cleanup. */ }
                if (!isCurrentPublication(uid, token, capturedSession)) return@launch
                val result = try {
                    action(capturedSession)
                } catch (timeout: TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive()
                    OperationResult.OutcomeUnknown(before.capsuleId, "Publication acknowledgment timed out.")
                }
                if (!isCurrentPublication(uid, token, capturedSession)) return@launch
                if (result is OperationResult.Success) {
                    _uiState.update { it.copy(
                        publicationPhase = PublicationPhase.SENT, statusOnly = true, title = "", text = "", originalPhoto = null,
                        localPhotoUri = null, photoName = null, photoSizeBytes = null,
                        publicationMessage = R.string.publication_sent) }
                } else {
                    _uiState.update { it.copy(
                        publicationPhase = PublicationPhase.OUTCOME_UNKNOWN,
                        canRetryUnchanged = result is OperationResult.OutcomeUnknown && result.capsuleId == before.capsuleId,
                        publicationMessage = R.string.publication_unknown) }
                }
                // Even a rejected request can have an older pending write; only saved evidence
                // may enable abandonment or restore editability after a preflight validation error.
                val recovered = repository?.let { recoverSaved(it, capturedSession) }
                if (!isCurrentPublication(uid, token, capturedSession)) return@launch
                if (recovered is OperationResult.Success) {
                    val record = recovered.data.firstOrNull { it.capsuleId == _uiState.value.capsuleId }
                    if (record != null) {
                        applyRecovered(record)
                        if (record.phase == PublicationPhase.FAILED && result.publicationFailure != null) {
                            _uiState.update { it.copy(publicationFailure = result.publicationFailure) }
                        }
                    } else if (result is OperationResult.Invalid && before.editable) _uiState.update {
                        before.copy(publicationBusy = false, publicationPhase = null,
                            publicationMessage = R.string.publication_invalid)
                    }
                }
                _uiState.update { it.copy(publicationBusy = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (isCurrentPublication(uid, token, capturedSession)) _uiState.update {
                    // A cleanup/read failure cannot revoke an already received publication ACK.
                    val sent = it.publicationPhase == PublicationPhase.SENT
                    it.copy(publicationBusy = false,
                        publicationPhase = if (sent) PublicationPhase.SENT else PublicationPhase.OUTCOME_UNKNOWN,
                        publicationMessage = if (sent) R.string.publication_sent else R.string.publication_unknown)
                }
            }
        }
    }

    /** Performs the coordinator's authoritative abandonment check before exposing an editable copy. */
    fun replaceFailedPublication(targetId: String? = requestedId) {
        if (!matchesSelection(targetId)) return
        val backend = repository ?: return
        val state = _uiState.value
        val uid = state.activeSessionUid ?: return
        if (!state.canAbandon || state.publicationBusy || state.recoveryBusy || state.recoveryBlocked) return
        val token = ++publicationToken
        val capturedSession = observedSession
        _uiState.update { it.copy(publicationBusy = true) }
        viewModelScope.launch {
            try {
                photoSaveMutex.withLock { /* Drain earlier compose saves before final cleanup. */ }
                if (!isCurrentPublication(uid, token, capturedSession)) return@launch
                val result = backend.abandonPublication(state.capsuleId, capturedSession)
                if (!isCurrentPublication(uid, token, capturedSession)) return@launch
                if (result is OperationResult.Success) {
                    val draft = result.data
                    requestedId = null
                    requestedSession = null
                    _uiState.value = CapsuleDraftUiState(activeSessionUid = uid,
                        capsuleId = draft.capsuleId, title = draft.title ?: "", text = draft.text,
                        originalPhoto = draft.originalPhoto, localPhotoUri = draft.localPhotoUri,
                        photoName = draft.originalPhoto?.let { "Photo" },
                        photoSizeBytes = draft.originalPhoto?.sizeBytes,
                        recipientUidInput = draft.recipientUid,
                        recipientMode = RecipientMode.SOMEONE_ELSE,
                        lockMode = draft.lock.mode, unlockAt = draft.lock.unlockAt,
                        creationZone = draft.creationZone,
                        locationTarget = draft.lock.location,
                        publicationMessage = R.string.publication_replacement_ready)
                } else {
                    _uiState.update { it.copy(publicationBusy = false, canAbandon = false,
                        publicationMessage = R.string.publication_recovery_failure) }
                    recoverPublicationState(requestedId, origin = capturedSession)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (isCurrentPublication(uid, token, capturedSession)) _uiState.update {
                    it.copy(publicationBusy = false, publicationMessage = R.string.publication_recovery_failure)
                }
            }
        }
    }

    /** Allows a blank new draft after confirmed completion, never after an uncertain outcome. */
    fun startNewAfterSent(targetId: String? = requestedId) {
        if (!matchesSelection(targetId)) return
        val state = _uiState.value
        if (state.publicationPhase != PublicationPhase.SENT || state.publicationBusy ||
            state.recoveryBusy || state.recoveryBlocked) return
        requestedId = null
        requestedSession = null
        _uiState.value = CapsuleDraftUiState(activeSessionUid = state.activeSessionUid)
        state.activeSessionUid?.let { selectFutureMe(it) }
        persistPhotoDraft()
    }

    /** Bind a button press to the verified operation and account generation that supplied it. */
    private fun matchesSelection(id: String?): Boolean =
        observedSession == accountSessionManager.currentSession() && id == requestedId &&
            (id == null || (requestedSession == observedSession && id == _uiState.value.capsuleId))

    /** Validates both generation token and live Auth UID before applying asynchronous results. */
    private fun isCurrentPublication(
        uid: String,
        token: Long,
        capturedSession: SessionSnapshot
    ): Boolean =
        token == publicationToken && _uiState.value.activeSessionUid == uid &&
            accountSessionManager.currentSession().uid == uid &&
            accountSessionManager.isSessionValid(capturedSession)

    /** Bounds read-only recovery waiting without canceling the independently owned send task. */
    private suspend fun recoverSaved(
        backend: CapsuleRepository,
        capturedSession: SessionSnapshot
    ) =
        withTimeoutOrNull(15_000L) {
            try {
                recoveryCoordinator?.recoverPublications(capturedSession) ?: backend.recoverPublications(capturedSession)
            } catch (timeout: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                OperationResult.Unavailable("Saved send recovery timed out.")
            }
        } ?: OperationResult.Unavailable("Saved send recovery timed out.")

    /** Resets draft state with a new unique capsule ID. */
    fun discardDraft() {
        if (!_uiState.value.editable) return
        val previous = _uiState.value.originalPhoto
        photoToken++
        val activeUid = accountSessionManager.currentSession().uid
        _uiState.value = CapsuleDraftUiState(activeSessionUid = activeUid)
        if (!activeUid.isNullOrBlank()) {
            selectFutureMe(activeUid)
        }
        persistPhotoDraft(previous)
    }
}
