package com.example.timecapsule.ui.s03

import com.example.timecapsule.domain.model.CapsuleSummary
import com.example.timecapsule.domain.model.ListFreshness
import com.example.timecapsule.domain.model.RecoveredPublication
import com.example.timecapsule.domain.session.SessionSnapshot

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
    val freshness: ListFreshness = ListFreshness.SERVER_CONFIRMED,
    val sessionSnapshot: SessionSnapshot? = null
) {
    val isStale: Boolean
        get() = freshness != ListFreshness.SERVER_CONFIRMED
}
