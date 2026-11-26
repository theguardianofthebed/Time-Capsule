package com.example.timecapsule.ui.s07

import com.example.timecapsule.domain.model.CapsuleSummary
import com.example.timecapsule.domain.model.ProtectedCapsuleContent

/** Participant role on S-07 Capsule Detail screen. */
enum class ParticipantRole { AUTHOR_COPY, RECIPIENT, NONE }

/** Immutable UI state for S-07 Capsule Detail screen. */
data class CapsuleDetailUiState(
    val isLoading: Boolean = false,
    val capsuleId: String = "",
    val summary: CapsuleSummary? = null,
    val role: ParticipantRole = ParticipantRole.NONE,
    val content: ProtectedCapsuleContent? = null,
    val isUnlocked: Boolean = false,
    val unlockReason: String? = null,
    val errorMessage: String? = null,
    val isAssetUnavailable: Boolean = false
)
