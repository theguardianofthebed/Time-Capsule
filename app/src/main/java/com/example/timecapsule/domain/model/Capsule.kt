package com.example.timecapsule.domain.model

import java.time.Instant

/** Prototype domain contract only. Schema/serialization must be team-reviewed. */
enum class LockMode { TIME, LOCATION, TIME_AND_LOCATION }

data class LocationTarget(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double = 150.0,
)

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
    val lock: CapsuleLock,
    val sendStatus: SendStatus,
    val availability: Availability = Availability.UNKNOWN,
)

/** Keep private content in its own access-controlled backend boundary. */
data class ProtectedCapsuleContent(
    val capsuleId: String,
    val text: String?,
    val photoObjectPath: String?,
)

/** Stable capsuleId must be reused across retries, never regenerated for the same send. */
data class CapsuleDraft(
    val capsuleId: String,
    val recipientUid: String,
    val text: String,
    val localPhotoUri: String?,
    val lock: CapsuleLock,
) {
    fun hasAnyContent(): Boolean = text.isNotBlank() || !localPhotoUri.isNullOrBlank()
}
