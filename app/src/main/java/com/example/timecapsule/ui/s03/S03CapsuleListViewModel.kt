package com.example.timecapsule.ui.s03

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timecapsule.data.CapsuleRepository
import com.example.timecapsule.data.recovery.ForegroundRecoveryCoordinator
import com.example.timecapsule.data.session.SessionTracker
import com.example.timecapsule.domain.model.PublicationPhase
import com.example.timecapsule.domain.model.SendStatus
import com.example.timecapsule.domain.result.OperationResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for S-03 Capsule Lists screen.
 * Orchestrates session-scoped list fetching, publication recovery, and foreground refresh.
 */
class S03CapsuleListViewModel(
    private val sessionTracker: SessionTracker,
    private val repository: CapsuleRepository,
    private val recoveryCoordinator: ForegroundRecoveryCoordinator? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(CapsuleListUiState(isLoading = true))
    val uiState: StateFlow<CapsuleListUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var requestToken: Long = 0L

    init {
        viewModelScope.launch {
            sessionTracker.observeSession().collect { session ->
                if (!session.isAuthenticated) {
                    _uiState.value = CapsuleListUiState(isLoading = false)
                } else {
                    loadLists()
                }
            }
        }
    }

    fun selectTab(tab: CapsuleTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun loadLists(explicitRefresh: Boolean = false) {
        val capturedSession = sessionTracker.currentSession()
        val uid = capturedSession.uid
        if (!capturedSession.isAuthenticated || uid.isNullOrBlank()) {
            _uiState.value = CapsuleListUiState(isLoading = false)
            return
        }

        val token = ++requestToken
        activeJob?.cancel()
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        activeJob = viewModelScope.launch {
            // 1. Fetch remote/retained lists
            val listsRes = repository.lists(uid, capturedSession)
            if (!sessionTracker.isSessionValid(capturedSession) || token != requestToken) return@launch

            // 2. Recover publications
            val recoveryRes = recoveryCoordinator?.recoverPublications(capturedSession)
            if (!sessionTracker.isSessionValid(capturedSession) || token != requestToken) return@launch

            val unresolved = if (recoveryRes is OperationResult.Success) {
                recoveryRes.data.filter { it.phase != PublicationPhase.SENT }
            } else {
                emptyList()
            }

            when (listsRes) {
                is OperationResult.Success -> {
                    val snapshot = listsRes.data
                    val isStale = snapshot.received.any { it.title?.contains("Previously unlocked") == true }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        receivedList = snapshot.received,
                        sentList = snapshot.sent,
                        unresolvedOperations = unresolved,
                        errorMessage = null,
                        isStale = isStale
                    )

                    // Check foreground for known recipient items to trigger background unlock checks
                    val knownRecipientIds = snapshot.received.map { it.capsuleId }
                    if (knownRecipientIds.isNotEmpty() && recoveryCoordinator != null) {
                        recoveryCoordinator.checkForeground(knownRecipientIds, capturedSession)
                    }
                }
                is OperationResult.Denied -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = listsRes.message
                    )
                }
                is OperationResult.Invalid -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = listsRes.message
                    )
                }
                is OperationResult.Unavailable -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = listsRes.message,
                        isStale = true
                    )
                }
                is OperationResult.OutcomeUnknown -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = listsRes.message
                    )
                }
            }
        }
    }
}
