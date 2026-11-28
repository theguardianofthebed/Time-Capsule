package com.example.timecapsule.domain.model

import java.time.Instant
import java.time.ZoneId

/** Prototype domain contract only. Schema/serialization must be team-reviewed. */
enum class LockMode { TIME, LOCATION, TIME_AND_LOCATION }

data class LocationTarget(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double = FIXED_RADIUS_METERS,
) {
    companion object {
        const val FIXED_RADIUS_METERS: Double = 150.0
    }
}

data class CapsuleLock(
    val mode: LockMode,
    val unlockAt: Instant? = null,
    val location: LocationTarget? = null,
)

enum class SendStatus { DRAFT, SENDING, SENT, FAILED, UNKNOWN }
enum class Availability { LOCKED, AVAILABLE, UNKNOWN }

/** Metadata does not include protected text/photo. UI availability is not proof of authorization. */
data class CapsuleSummary(
    val capsuleId: String,
    val senderUid: String,
    val recipientUid: String,
    val lock: CapsuleLock?,
    val sendStatus: SendStatus,
    val availability: Availability = Availability.UNKNOWN,
    val title: String? = null,
    val createdAt: Instant? = null,
    val creationZone: String? = null,
    val hasPhoto: Boolean = false,
    val unlockedAt: Instant? = null,
    val senderDisplayName: String? = null,
    val recipientDisplayName: String? = null,
)

/** Keep private content in its own access-controlled backend boundary. */
data class ProtectedCapsuleContent(
    val capsuleId: String,
    val text: String?,
    val photoObjectPath: String?,
    val photoBytes: ByteArray? = null,
    val photoFormat: OriginalPhotoFormat? = null,
    val availableOffline: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ProtectedCapsuleContent
        if (capsuleId != other.capsuleId) return false
        if (text != other.text) return false
        if (photoObjectPath != other.photoObjectPath) return false
        if (photoBytes != null) {
            if (other.photoBytes == null) return false
            if (!photoBytes.contentEquals(other.photoBytes)) return false
        } else if (other.photoBytes != null) return false
        if (availableOffline != other.availableOffline || photoFormat != other.photoFormat) return false
        return true
    }

    override fun hashCode(): Int {
        var result = capsuleId.hashCode()
        result = 31 * result + (text?.hashCode() ?: 0)
        result = 31 * result + (photoObjectPath?.hashCode() ?: 0)
        result = 31 * result + (photoBytes?.contentHashCode() ?: 0)
        result = 31 * result + (photoFormat?.hashCode() ?: 0)
        return 31 * result + availableOffline.hashCode()
    }
}

/**
 * Stable capsuleId must be reused across retries, never regenerated for the same send.
 * [originalPhoto] carries validated private bytes for #13's freeze/upload handoff; a URI alone
 * does not establish photo validity or upload acknowledgment. Publication remains owned by #14.
 */
data class CapsuleDraft(
    val capsuleId: String,
    val recipientUid: String,
    val text: String,
    val localPhotoUri: String?,
    val lock: CapsuleLock,
    val title: String? = null,
    val recipientDisplayName: String? = null,
    val isRecipientConfirmed: Boolean = false,
    val locationName: String? = null,
    val originalPhoto: RetainedOriginalPhoto? = null,
    val creationZone: String = ZoneId.systemDefault().id,
) {
    fun hasAnyContent(): Boolean = text.isNotBlank() || !localPhotoUri.isNullOrBlank()
}
