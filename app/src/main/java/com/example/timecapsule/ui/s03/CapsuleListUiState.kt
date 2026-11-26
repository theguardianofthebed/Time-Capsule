package com.example.timecapsule.ui.s03

import com.example.timecapsule.domain.model.CapsuleSummary
import com.example.timecapsule.domain.model.RecoveredPublication

/** Selected delivery direction tab on S-03 Capsule List screen. */
enum class CapsuleTab { RECEIVED, SENT }

/** Immutable UI state for S-03 Capsule List screen. */
data class CapsuleListUiState(
    val isLoading: Boolean = false,
    val selectedTab: CapsuleTab = CapsuleTab.RECEIVED,
    val receivedList: List<CapsuleSummary> = emptyList(),
    val sentList: List<CapsuleSummary> = emptyList(),
    val unresolvedOperations: List<RecoveredPublication> = emptyList(),
    val errorMessage: String? = null,
    val isStale: Boolean = false
)
