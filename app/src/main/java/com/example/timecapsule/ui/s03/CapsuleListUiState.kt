package com.example.timecapsule.ui.s03

import com.example.timecapsule.domain.model.CapsuleSummary
import com.example.timecapsule.domain.model.ListProvenance
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
    val receivedProvenance: ListProvenance = ListProvenance.UNAVAILABLE,
    val sentProvenance: ListProvenance = ListProvenance.UNAVAILABLE,
    val sessionSnapshot: SessionSnapshot? = null
) {
    val selectedProvenance get() = if (selectedTab == CapsuleTab.RECEIVED) receivedProvenance else sentProvenance
    val isReceivedUnavailable get() = receivedProvenance == ListProvenance.UNAVAILABLE
    val isSentUnavailable get() = sentProvenance == ListProvenance.UNAVAILABLE
    val isStale get() = !isLoading && selectedProvenance != ListProvenance.SERVER_CONFIRMED
}
