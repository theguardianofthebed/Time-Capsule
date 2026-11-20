package com.example.timecapsule.ui.draft

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.example.timecapsule.data.media.AndroidPhotoInput
import com.example.timecapsule.data.media.PhotoDraftRecord
import com.example.timecapsule.domain.model.RetainedOriginalPhoto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import com.example.timecapsule.data.AccountSessionManager
import com.example.timecapsule.domain.model.CapsuleDraft
import com.example.timecapsule.domain.model.CapsuleLock
import com.example.timecapsule.domain.model.LocationTarget
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.domain.model.RecipientConfirmation
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.result.OperationResult
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val photoError: String? = null,
    val recipientMode: RecipientMode = RecipientMode.FUTURE_ME,
    val recipientUidInput: String = "",
    val lookupStatus: RecipientLookupStatus = RecipientLookupStatus.IDLE,
    val lookupErrorMessage: String? = null,
    val resolvedProfile: RecipientProfile? = null,
    val isRecipientConfirmed: Boolean = false,
    val lockMode: LockMode = LockMode.TIME,
    val unlockAt: Instant? = null,
    val locationTarget: LocationTarget? = null,
    val locationName: String? = null,
    val sendNotice: String? = null,
    val activeSessionUid: String? = null
)

/**
 * ViewModel managing the shared draft state for S-04 (Content), S-05 (Recipient & Locks), and S-06 (Review).
 * Preserves draft state across navigation, enforces recipient lookup and explicit confirmation,
 * rejects stale asynchronous lookup callbacks, and invalidates state on account/session changes.
 */
class CapsuleDraftViewModel(
    private val accountSessionManager: AccountSessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CapsuleDraftUiState())
    val uiState: StateFlow<CapsuleDraftUiState> = _uiState.asStateFlow()

    private var activeLookupToken: Long = 0L
    private var photoToken = 0L
    private var photoInput: AndroidPhotoInput? = null
    private val photoSaveMutex = Mutex()

    /** Configures account-private persistence once for this activity-scoped ViewModel. */
    fun configurePhotos(input: AndroidPhotoInput) {
        if (photoInput != null) return
        photoInput = input
        restorePhotoDraft()
    }

    /** Restores only the still-active account; recipient confirmation must be established again. */
    private fun restorePhotoDraft() {
        val input = photoInput ?: return
        val uid = _uiState.value.activeSessionUid ?: return
        val token = ++photoToken
        _uiState.update { it.copy(photoBusy = true) }
        viewModelScope.launch {
            try {
                val record = input.restore(uid)
                if (token != photoToken || _uiState.value.activeSessionUid != uid) return@launch
                _uiState.update { state ->
                    if (record == null) state.copy(photoBusy = false) else state.copy(
                        capsuleId = record.capsuleId, title = record.title, text = record.text,
                        originalPhoto = record.photo,
                        localPhotoUri = record.photo?.let { Uri.fromFile(input.file(it)).toString() },
                        photoName = record.photo?.let { "Photo" },
                        photoSizeBytes = record.photo?.sizeBytes, photoBusy = false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                photoFailed(token, "Saved photo cannot be restored. Choose it again.")
            } catch (error: IllegalArgumentException) {
                photoFailed(token, "Saved photo information is invalid. Choose it again.")
            }
        }
    }

    /** Captures editable fields now and queues their atomic save in UI event order. */
    private fun persistPhotoDraft() {
        val input = photoInput ?: return
        val state = _uiState.value
        val uid = state.activeSessionUid ?: return
        val record = PhotoDraftRecord(state.capsuleId, state.title, state.text, state.originalPhoto)
        viewModelScope.launch {
            try {
                photoSaveMutex.withLock { input.save(uid, record) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                if (_uiState.value.activeSessionUid == uid) {
                    _uiState.update { it.copy(photoError = "Cannot save draft. Free storage and retry.") }
                }
            }
        }
    }

    /** Retains provider content asynchronously and ignores obsolete selections or accounts. */
    fun selectPhoto(input: AndroidPhotoInput, uri: Uri) {
        val state = _uiState.value
        val uid = state.activeSessionUid ?: return
        val token = ++photoToken
        _uiState.update { it.copy(photoBusy = true, photoError = null) }
        viewModelScope.launch {
            try {
                val photo = input.retain(uid, state.capsuleId, uri)
                if (token != photoToken || _uiState.value.activeSessionUid != uid ||
                    accountSessionManager.restoreSessionIfAuthenticated() != uid ||
                    _uiState.value.capsuleId != state.capsuleId) {
                    input.remove(photo)
                    return@launch
                }
                val previous = _uiState.value.originalPhoto
                _uiState.update { it.copy(originalPhoto = photo,
                    localPhotoUri = Uri.fromFile(input.file(photo)).toString(),
                    photoName = "Photo", photoSizeBytes = photo.sizeBytes, photoBusy = false) }
                // Persist the new pointer before releasing the old bytes; failure keeps both recoverable.
                val current = _uiState.value
                photoSaveMutex.withLock {
                    input.save(uid, PhotoDraftRecord(current.capsuleId, current.title, current.text, photo))
                }
                if (previous != null) input.remove(previous)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: SecurityException) {
                photoFailed(token, "Photo access was denied. Choose the photo again.")
            } catch (error: IOException) {
                photoFailed(token, error.message ?: "Cannot read photo. Choose it again or retry.")
            }
        }
    }

    /** Updates only the current request's error without replacing a previous valid selection. */
    private fun photoFailed(token: Long, message: String) {
        if (token == photoToken) _uiState.update { it.copy(photoBusy = false, photoError = message) }
    }

    init {
        val currentUid = accountSessionManager.restoreSessionIfAuthenticated()
        _uiState.update { it.copy(activeSessionUid = currentUid) }
        if (!currentUid.isNullOrBlank()) {
            selectFutureMe(currentUid)
        }
    }

    /**
     * Re-evaluates session state to prevent cross-session leakage.
     * Clears draft or recipient confirmation if user session has changed.
     */
    fun syncSessionState() {
        val activeUid = accountSessionManager.restoreSessionIfAuthenticated()
        if (activeUid != _uiState.value.activeSessionUid) {
            photoToken++
            activeLookupToken++
            _uiState.value = CapsuleDraftUiState(activeSessionUid = activeUid)
            if (!activeUid.isNullOrBlank()) {
                selectFutureMe(activeUid)
                restorePhotoDraft()
            }
        }
    }

    /** Updates optional capsule title. */
    fun updateTitle(newTitle: String) {
        _uiState.update { it.copy(title = newTitle) }
        persistPhotoDraft()
    }

    /** Updates message body text. */
    fun updateText(newText: String) {
        _uiState.update { it.copy(text = newText) }
        persistPhotoDraft()
    }

    /**
     * Attaches one photo boundary URI and enforces maximum 5 MiB size validation.
     */
    fun attachPhoto(uri: String, fileName: String, sizeBytes: Long): OperationResult<Unit> {
        if (sizeBytes > 5 * 1024 * 1024) {
            return OperationResult.Invalid("Photo exceeds maximum size of 5 MiB (5,242,880 bytes).")
        }
        _uiState.update {
            it.copy(
                localPhotoUri = uri,
                photoName = fileName,
                photoSizeBytes = sizeBytes
            )
        }
        return OperationResult.Success(Unit)
    }

    /** Removes attached photo. */
    fun removePhoto() {
        photoToken++
        _uiState.update {
            it.copy(
                localPhotoUri = null,
                photoName = null,
                photoSizeBytes = null,
                originalPhoto = null, photoBusy = false, photoError = null
            )
        }
        persistPhotoDraft()
    }

    /** Selects recipient mode: Future Me or Someone Else. */
    fun setRecipientMode(mode: RecipientMode) {
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
            val currentUid = accountSessionManager.restoreSessionIfAuthenticated() ?: _uiState.value.activeSessionUid ?: ""
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
        _uiState.update { it.copy(lockMode = mode) }
    }

    /** Updates target unlock date/time instant. */
    fun updateUnlockAt(instant: Instant?) {
        _uiState.update { it.copy(unlockAt = instant) }
    }

    /** Updates target location coordinates and location name. */
    fun updateLocation(location: LocationTarget?, name: String?) {
        _uiState.update {
            it.copy(
                locationTarget = location,
                locationName = name
            )
        }
    }

    /** Validates S-04 content requirements: message body or photo required. Title alone is invalid. */
    fun validateS04Content(): OperationResult<Unit> {
        val state = _uiState.value
        if (state.photoBusy) return OperationResult.Invalid("Please wait for the photo to finish.")
        val hasText = state.text.isNotBlank()
        val hasPhoto = !state.localPhotoUri.isNullOrBlank()
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
            locationName = state.locationName
        )
    }

    /** Triggers a clear notice that publication belongs to downstream Issue #14. */
    fun triggerSendNotice() {
        _uiState.update {
            it.copy(sendNotice = "Sending isn't available yet. Your draft has been saved safely on this device.")
        }
    }

    /** Clears send notice message. */
    fun clearSendNotice() {
        _uiState.update { it.copy(sendNotice = null) }
    }

    /** Resets draft state with a new unique capsule ID. */
    fun discardDraft() {
        photoToken++
        val activeUid = accountSessionManager.restoreSessionIfAuthenticated()
        _uiState.value = CapsuleDraftUiState(activeSessionUid = activeUid)
        if (!activeUid.isNullOrBlank()) {
            selectFutureMe(activeUid)
        }
        persistPhotoDraft()
    }
}
