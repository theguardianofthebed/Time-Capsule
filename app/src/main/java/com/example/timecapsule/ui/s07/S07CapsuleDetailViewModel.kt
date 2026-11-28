package com.example.timecapsule.ui.s07

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timecapsule.data.CapsuleRepository
import com.example.timecapsule.data.recovery.ForegroundRecoveryCoordinator
import com.example.timecapsule.data.session.SessionTracker
import com.example.timecapsule.domain.model.Availability
import com.example.timecapsule.domain.model.CapsuleSummary
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for S-07 Capsule Detail screen.
 * Orchestrates role-aware metadata lookup, unlock condition evaluation, and protected content reads.
 */
class S07CapsuleDetailViewModel(
    private val sessionTracker: SessionTracker,
    private val repository: CapsuleRepository,
    private val recoveryCoordinator: ForegroundRecoveryCoordinator? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(CapsuleDetailUiState(isLoading = true))
    val uiState: StateFlow<CapsuleDetailUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var requestToken: Long = 0L

    init {
        viewModelScope.launch {
            sessionTracker.observeSession().collect { session ->
                val currentSnap = _uiState.value.sessionSnapshot
                if (currentSnap != null && currentSnap != session) {
                    activeJob?.cancel()
                    requestToken++
                    val currentId = _uiState.value.capsuleId
                    _uiState.value = CapsuleDetailUiState(isLoading = false, sessionSnapshot = session)
                    if (currentId.isNotBlank()) {
                        loadDetail(currentId)
                    }
                }
            }
        }
    }

    /** Keep the requested identity while waiting for auth, then load under the current generation. */
    fun loadDetail(capsuleId: String) {
        val token = ++requestToken
        activeJob?.cancel()
        val cleanId = capsuleId.trim()
        if (cleanId.isBlank()) {
            _uiState.value = CapsuleDetailUiState(
                isLoading = false,
                status = DetailStatus.ERROR,
                errorMessage = "Capsule ID is required."
            )
            return
        }

        val capturedSession = sessionTracker.currentSession()
        val activeUid = capturedSession.uid
        if (!capturedSession.isAuthenticated || activeUid.isNullOrBlank()) {
            _uiState.value = CapsuleDetailUiState(
                isLoading = false,
                capsuleId = cleanId, sessionSnapshot = capturedSession,
                status = DetailStatus.LOADING,
                errorMessage = "Waiting for account restoration."
            )
            return
        }

        _uiState.value = CapsuleDetailUiState(
            isLoading = true,
            capsuleId = cleanId,
            status = DetailStatus.LOADING,
            sessionSnapshot = capturedSession
        )

        activeJob = viewModelScope.launch {
            // 1. Fetch metadata summary
            val summaryRes = repository.getSummary(cleanId, capturedSession)
            if (!sessionTracker.isSessionValid(capturedSession) || token != requestToken) return@launch

            if (summaryRes !is OperationResult.Success) {
                val msg = resultErrorMessage(summaryRes)
                _uiState.value = CapsuleDetailUiState(
                    isLoading = false,
                    capsuleId = cleanId,
                    status = if (summaryRes is OperationResult.Denied) DetailStatus.ACCESS_DENIED else DetailStatus.ERROR,
                    errorMessage = msg,
                    sessionSnapshot = capturedSession
                )
                return@launch
            }

            val summary = summaryRes.data
            val role = when {
                activeUid == summary.recipientUid -> ParticipantRole.RECIPIENT
                activeUid == summary.senderUid && summary.senderUid != summary.recipientUid -> ParticipantRole.AUTHOR_COPY
                else -> ParticipantRole.NONE
            }

            if (role == ParticipantRole.NONE) {
                _uiState.value = CapsuleDetailUiState(
                    isLoading = false,
                    capsuleId = cleanId,
                    summary = summary,
                    role = ParticipantRole.NONE,
                    status = DetailStatus.ACCESS_DENIED,
                    errorMessage = "Access denied. User is not a participant of this capsule.",
                    sessionSnapshot = capturedSession
                )
                return@launch
            }

            if (role == ParticipantRole.AUTHOR_COPY) {
                // Author Copy branch: direct read without unlock transaction
                val contentRes = repository.readContent(cleanId, capturedSession)
                if (!sessionTracker.isSessionValid(capturedSession) || token != requestToken) return@launch

                if (contentRes is OperationResult.Denied) {
                    _uiState.value = CapsuleDetailUiState(capsuleId = cleanId, sessionSnapshot = capturedSession,
                        status = DetailStatus.ACCESS_DENIED, errorMessage = contentRes.message)
                    return@launch
                }
                when (contentRes) {
                    is OperationResult.Success -> {
                        _uiState.value = CapsuleDetailUiState(
                            isLoading = false,
                            capsuleId = cleanId,
                            summary = summary,
                            role = ParticipantRole.AUTHOR_COPY,
                            content = contentRes.data,
                            isUnlocked = false,
                            status = DetailStatus.AUTHOR_COPY_READY,
                            unlockReason = "Sender Copy",
                            sessionSnapshot = capturedSession
                        )
                    }
                    else -> {
                        _uiState.value = CapsuleDetailUiState(
                            isLoading = false,
                            capsuleId = cleanId,
                            summary = summary,
                            role = ParticipantRole.AUTHOR_COPY,
                            status = DetailStatus.AUTHOR_COPY_ASSET_UNAVAILABLE,
                            errorMessage = resultErrorMessage(contentRes),
                            isAssetUnavailable = true,
                            sessionSnapshot = capturedSession
                        )
                    }
                }
                return@launch
            }

            // Recipient branch:
            var isUnlocked = (summary.availability == Availability.AVAILABLE)
            var failureReason: String? = null
            var failureStatus: DetailStatus? = null

            if (!isUnlocked && recoveryCoordinator != null) {
                val verifyRes = recoveryCoordinator.verifyUnlock(cleanId, capturedSession)
                if (!sessionTracker.isSessionValid(capturedSession) || token != requestToken) return@launch
                if (verifyRes is OperationResult.Success && verifyRes.data == Availability.AVAILABLE) {
                    isUnlocked = true
                } else if (verifyRes !is OperationResult.Success) {
                    failureReason = resultErrorMessage(verifyRes)
                    failureStatus = mapUnlockFailureStatus(verifyRes, summary.lock?.mode)
                }
            }

            if (!isUnlocked) {
                val lockedStatus = failureStatus ?: when (summary.lock?.mode) {
                    null -> DetailStatus.LOCKED_VERIFICATION_FAILED
                    LockMode.TIME -> DetailStatus.LOCKED_WAITING_TIME
                    LockMode.LOCATION -> DetailStatus.LOCKED_WAITING_LOCATION
                    LockMode.TIME_AND_LOCATION -> DetailStatus.LOCKED_WAITING_BOTH
                }
                _uiState.value = CapsuleDetailUiState(
                    isLoading = false,
                    capsuleId = cleanId,
                    summary = summary,
                    role = ParticipantRole.RECIPIENT,
                    isUnlocked = false,
                    status = lockedStatus,
                    unlockReason = failureReason ?: formatLockReason(summary),
                    errorMessage = failureReason,
                    sessionSnapshot = capturedSession
                )
                return@launch
            }

            // Unlocked recipient -> Read protected content
            val contentRes = repository.readContent(cleanId, capturedSession)
            if (!sessionTracker.isSessionValid(capturedSession) || token != requestToken) return@launch

            if (contentRes is OperationResult.Denied) {
                _uiState.value = CapsuleDetailUiState(capsuleId = cleanId, sessionSnapshot = capturedSession,
                    status = DetailStatus.ACCESS_DENIED, errorMessage = contentRes.message)
                return@launch
            }
            when (contentRes) {
                is OperationResult.Success -> {
                    _uiState.value = CapsuleDetailUiState(
                        isLoading = false,
                        capsuleId = cleanId,
                        summary = summary,
                        role = ParticipantRole.RECIPIENT,
                        content = contentRes.data,
                        isUnlocked = true,
                        status = DetailStatus.UNLOCKED_CONTENT_READY,
                        unlockReason = "Capsule unlocked",
                        sessionSnapshot = capturedSession
                    )
                }
                else -> {
                    _uiState.value = CapsuleDetailUiState(
                        isLoading = false,
                        capsuleId = cleanId,
                        summary = summary,
                        role = ParticipantRole.RECIPIENT,
                        isUnlocked = true,
                        status = DetailStatus.UNLOCKED_ASSET_UNAVAILABLE,
                        errorMessage = resultErrorMessage(contentRes),
                        isAssetUnavailable = true,
                        sessionSnapshot = capturedSession
                    )
                }
            }
        }
    }

    /** Keep the verified detail and any readable text when the current photo cannot be displayed. */
    fun photoDecodeFailed(id: String, session: SessionSnapshot) {
        val state = _uiState.value
        val content = state.content ?: return
        if (state.capsuleId != id || state.sessionSnapshot != session || !sessionTracker.isSessionValid(session)) return
        _uiState.value = state.copy(content = content.copy(photoBytes = null), isAssetUnavailable = true,
            status = if (state.role == ParticipantRole.AUTHOR_COPY) DetailStatus.AUTHOR_COPY_ASSET_UNAVAILABLE
                else DetailStatus.UNLOCKED_ASSET_UNAVAILABLE,
            errorMessage = "Could not display the photo. Retry to load it again.")
    }

    /** Distinguish device location guidance from an authoritative participant denial. */
    private fun mapUnlockFailureStatus(res: OperationResult<*>, lockMode: LockMode?): DetailStatus {
        val msg = resultErrorMessage(res)
        val isLocationRequired = lockMode == LockMode.LOCATION || lockMode == LockMode.TIME_AND_LOCATION

        // 1. Check precise location guidance for location-required locks
        if (isLocationRequired) {
            if (msg.contains("location permission", ignoreCase = true) ||
                (msg.contains("location", ignoreCase = true) && msg.contains("permission", ignoreCase = true))) {
                return DetailStatus.LOCKED_PERMISSION_REQUIRED
            }
            if (msg.contains("location service disabled", ignoreCase = true) ||
                msg.contains("location services disabled", ignoreCase = true) ||
                msg.contains("location provider disabled", ignoreCase = true) ||
                msg.contains("gps disabled", ignoreCase = true) ||
                (msg.contains("location", ignoreCase = true) && msg.contains("disabled", ignoreCase = true) && !msg.contains("account", ignoreCase = true))) {
                return DetailStatus.LOCKED_LOCATION_DISABLED
            }
            if (msg.contains("null fix", ignoreCase = true) ||
                msg.contains("location accuracy", ignoreCase = true) ||
                msg.contains("stale location", ignoreCase = true) ||
                msg.contains("outside target radius", ignoreCase = true) ||
                msg.contains("sample is missing", ignoreCase = true)) {
                return DetailStatus.LOCKED_WAITING_LOCATION
            }
        }

        // 2. Time condition pending
        val isTimeConditionPending = msg.contains("unlock time not reached", ignoreCase = true) ||
                msg.contains("time not reached", ignoreCase = true) ||
                msg.contains("time condition", ignoreCase = true)
        if (isTimeConditionPending) {
            return when (lockMode) {
                LockMode.LOCATION -> DetailStatus.LOCKED_WAITING_LOCATION
                LockMode.TIME_AND_LOCATION -> DetailStatus.LOCKED_WAITING_BOTH
                LockMode.TIME, null -> DetailStatus.LOCKED_WAITING_TIME
            }
        }

        // 3. Verification service / network unavailable
        if (res is OperationResult.Unavailable) {
            return DetailStatus.LOCKED_VERIFICATION_FAILED
        }

        // 4. Access / session / participant denial
        if (res is OperationResult.Denied) {
            return DetailStatus.ACCESS_DENIED
        }

        return when (lockMode) {
            LockMode.TIME -> DetailStatus.LOCKED_WAITING_TIME
            LockMode.LOCATION -> DetailStatus.LOCKED_WAITING_LOCATION
            LockMode.TIME_AND_LOCATION -> DetailStatus.LOCKED_WAITING_BOTH
            null -> DetailStatus.LOCKED_VERIFICATION_FAILED
        }
    }

    private fun resultErrorMessage(res: OperationResult<*>): String = when (res) {
        is OperationResult.Invalid -> res.message
        is OperationResult.Denied -> res.message
        is OperationResult.Unavailable -> res.message
        is OperationResult.OutcomeUnknown -> res.message
        is OperationResult.Success -> ""
    }

    /** Describe only the lock metadata available for this capsule. */
    private fun formatLockReason(summary: CapsuleSummary): String {
        return when (summary.lock?.mode) {
            null -> "Lock details unavailable"
            LockMode.TIME -> "Locked by time"
            LockMode.LOCATION -> "Locked by location"
            LockMode.TIME_AND_LOCATION -> "Locked by time + location"
        }
    }
}
