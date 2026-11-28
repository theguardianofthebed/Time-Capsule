package com.example.timecapsule.ui.s07

import com.example.timecapsule.domain.model.CapsuleSummary
import com.example.timecapsule.domain.model.ProtectedCapsuleContent
import com.example.timecapsule.domain.session.SessionSnapshot

/** Participant role on S-07 Capsule Detail screen. */
enum class ParticipantRole { AUTHOR_COPY, RECIPIENT, NONE }

/** Detailed presentation status for S-07 Capsule Detail screen. */
enum class DetailStatus {
    LOADING,
    LOCKED_WAITING_TIME,
    LOCKED_WAITING_LOCATION,
    LOCKED_WAITING_BOTH,
    LOCKED_VERIFICATION_FAILED,
    UNLOCKED_CONTENT_READY,
    UNLOCKED_ASSET_UNAVAILABLE,
    AUTHOR_COPY_READY,
    AUTHOR_COPY_ASSET_UNAVAILABLE,
    ACCESS_DENIED,
    ERROR
}

/** Immutable UI state for S-07 Capsule Detail screen. */
data class CapsuleDetailUiState(
    val isLoading: Boolean = false,
    val capsuleId: String = "",
    val summary: CapsuleSummary? = null,
    val role: ParticipantRole = ParticipantRole.NONE,
    val content: ProtectedCapsuleContent? = null,
    val isUnlocked: Boolean = false,
    val status: DetailStatus = DetailStatus.LOADING,
    val unlockReason: String? = null,
    val errorMessage: String? = null,
    val isAssetUnavailable: Boolean = false,
    val sessionSnapshot: SessionSnapshot? = null
)
