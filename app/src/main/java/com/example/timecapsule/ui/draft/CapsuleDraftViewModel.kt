package com.example.timecapsule.ui.draft

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
            _uiState.value = CapsuleDraftUiState(activeSessionUid = activeUid)
            if (!activeUid.isNullOrBlank()) {
                selectFutureMe(activeUid)
            }
        }
    }

    /** Updates optional capsule title. */
    fun updateTitle(newTitle: String) {
        _uiState.update { it.copy(title = newTitle) }
    }

    /** Updates message body text. */
    fun updateText(newText: String) {
        _uiState.update { it.copy(text = newText) }
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
        _uiState.update {
            it.copy(
                localPhotoUri = null,
                photoName = null,
                photoSizeBytes = null
            )
        }
    }

    /** Selects recipient mode: Future Me or Someone Else. */
    fun setRecipientMode(mode: RecipientMode) {
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
     * CRITICAL: Resets confirmation and clears resolved profile if the input text changes.
     */
    fun updateRecipientUidInput(newUidText: String) {
        val trimmed = newUidText.trim()
        val previousInput = _uiState.value.recipientUidInput.trim()
        val textChanged = trimmed != previousInput

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
            it.copy(sendNotice = "Publication belongs to downstream Issue #14. Draft preserved safely on device.")
        }
    }

    /** Clears send notice message. */
    fun clearSendNotice() {
        _uiState.update { it.copy(sendNotice = null) }
    }

    /** Resets draft state with a new unique capsule ID. */
    fun discardDraft() {
        val activeUid = accountSessionManager.restoreSessionIfAuthenticated()
        _uiState.value = CapsuleDraftUiState(activeSessionUid = activeUid)
        if (!activeUid.isNullOrBlank()) {
            selectFutureMe(activeUid)
        }
    }
}
