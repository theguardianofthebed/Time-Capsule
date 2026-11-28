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
                activeJob?.cancel()
                requestToken++
                if (!session.isAuthenticated) {
                    _uiState.value = CapsuleDetailUiState(isLoading = false, sessionSnapshot = session)
                } else {
                    val currentId = _uiState.value.capsuleId
                    _uiState.value = CapsuleDetailUiState(isLoading = false, sessionSnapshot = session)
                    if (currentId.isNotBlank()) {
                        loadDetail(currentId)
                    }
                }
            }
        }
    }

    fun loadDetail(capsuleId: String) {
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
                status = DetailStatus.ACCESS_DENIED,
                errorMessage = "Authentication required."
            )
            return
        }

        val token = ++requestToken
        activeJob?.cancel()
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
                    status = DetailStatus.ERROR,
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

            if (!isUnlocked && recoveryCoordinator != null) {
                val verifyRes = recoveryCoordinator.verifyUnlock(cleanId, capturedSession)
                if (!sessionTracker.isSessionValid(capturedSession) || token != requestToken) return@launch
                if (verifyRes is OperationResult.Success && verifyRes.data == Availability.AVAILABLE) {
                    isUnlocked = true
                }
            }

            if (!isUnlocked) {
                val lockedStatus = when (summary.lock.mode) {
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
                    unlockReason = formatLockReason(summary),
                    sessionSnapshot = capturedSession
                )
                return@launch
            }

            // Unlocked recipient -> Read protected content
            val contentRes = repository.readContent(cleanId, capturedSession)
            if (!sessionTracker.isSessionValid(capturedSession) || token != requestToken) return@launch

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

    private fun resultErrorMessage(res: OperationResult<*>): String = when (res) {
        is OperationResult.Invalid -> res.message
        is OperationResult.Denied -> res.message
        is OperationResult.Unavailable -> res.message
        is OperationResult.OutcomeUnknown -> res.message
        is OperationResult.Success -> ""
    }

    private fun formatLockReason(summary: CapsuleSummary): String {
        return when (summary.lock.mode) {
            LockMode.TIME -> "Locked by time"
            LockMode.LOCATION -> "Locked by location"
            LockMode.TIME_AND_LOCATION -> "Locked by time + location"
        }
    }
}
