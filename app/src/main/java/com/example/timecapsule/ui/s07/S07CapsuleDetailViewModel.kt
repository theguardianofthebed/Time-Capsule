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

    fun loadDetail(capsuleId: String) {
        val cleanId = capsuleId.trim()
        if (cleanId.isBlank()) {
            _uiState.value = CapsuleDetailUiState(isLoading = false, errorMessage = "Capsule ID is required.")
            return
        }

        val capturedSession = sessionTracker.currentSession()
        val activeUid = capturedSession.uid
        if (!capturedSession.isAuthenticated || activeUid.isNullOrBlank()) {
            _uiState.value = CapsuleDetailUiState(isLoading = false, errorMessage = "Authentication required.")
            return
        }

        val token = ++requestToken
        activeJob?.cancel()
        _uiState.value = CapsuleDetailUiState(isLoading = true, capsuleId = cleanId)

        activeJob = viewModelScope.launch {
            // 1. Fetch metadata summary
            val summaryRes = repository.getSummary(cleanId, capturedSession)
            if (!sessionTracker.isSessionValid(capturedSession) || token != requestToken) return@launch

            if (summaryRes !is OperationResult.Success) {
                val msg = when (summaryRes) {
                    is OperationResult.Invalid -> summaryRes.message
                    is OperationResult.Denied -> summaryRes.message
                    is OperationResult.Unavailable -> summaryRes.message
                    is OperationResult.OutcomeUnknown -> summaryRes.message
                    is OperationResult.Success -> ""
                }
                _uiState.value = CapsuleDetailUiState(
                    isLoading = false,
                    capsuleId = cleanId,
                    errorMessage = msg
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
                    errorMessage = "Access denied. User is not a participant of this capsule."
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
                            unlockReason = "Sender Copy"
                        )
                    }
                    else -> {
                        _uiState.value = CapsuleDetailUiState(
                            isLoading = false,
                            capsuleId = cleanId,
                            summary = summary,
                            role = ParticipantRole.AUTHOR_COPY,
                            errorMessage = resultErrorMessage(contentRes),
                            isAssetUnavailable = true
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
                _uiState.value = CapsuleDetailUiState(
                    isLoading = false,
                    capsuleId = cleanId,
                    summary = summary,
                    role = ParticipantRole.RECIPIENT,
                    isUnlocked = false,
                    unlockReason = formatLockReason(summary)
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
                        unlockReason = "Capsule unlocked"
                    )
                }
                else -> {
                    _uiState.value = CapsuleDetailUiState(
                        isLoading = false,
                        capsuleId = cleanId,
                        summary = summary,
                        role = ParticipantRole.RECIPIENT,
                        isUnlocked = true,
                        errorMessage = resultErrorMessage(contentRes),
                        isAssetUnavailable = true
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
