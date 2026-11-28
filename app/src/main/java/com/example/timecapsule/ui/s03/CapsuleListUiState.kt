package com.example.timecapsule.ui.s03

import com.example.timecapsule.domain.model.CapsuleSummary
import com.example.timecapsule.domain.model.ListFreshness
import com.example.timecapsule.domain.model.PublicationPhase
import com.example.timecapsule.domain.session.SessionSnapshot

/** Selected delivery direction tab on S-03 Capsule List screen. */
enum class CapsuleTab { RECEIVED, SENT }

/** Status-only presentation summary for unresolved publication operations (no raw draft payloads). */
data class UnresolvedPublicationSummary(
    val capsuleId: String,
    val phase: PublicationPhase,
    val messageResId: Int? = null
)

/** Immutable UI state for S-03 Capsule List screen. */
data class CapsuleListUiState(
    val isLoading: Boolean = false,
    val selectedTab: CapsuleTab = CapsuleTab.RECEIVED,
    val receivedList: List<CapsuleSummary> = emptyList(),
    val sentList: List<CapsuleSummary> = emptyList(),
    val unresolvedOperations: List<UnresolvedPublicationSummary> = emptyList(),
    val errorMessage: String? = null,
    val freshness: ListFreshness = ListFreshness.SERVER_CONFIRMED,
    val isReceivedUnavailable: Boolean = false,
    val isSentUnavailable: Boolean = false,
    val sessionSnapshot: SessionSnapshot? = null
) {
    val isStale: Boolean
        get() = freshness != ListFreshness.SERVER_CONFIRMED
}
