package com.example.timecapsule.domain.model

import java.time.Instant

/**
 * Server-authoritative recipient unlock requirements snapshot.
 * Fetched via server-only read prior to requesting location or executing an unlock transaction.
 *
 * @property capsuleId Unique capsule identifier.
 * @property recipientUid Designated recipient Auth UID.
 * @property unlocked Monotonic server unlocked status (true if already unlocked).
 * @property lockMode Lock mode configured for this capsule (TIME, LOCATION, or TIME_AND_LOCATION).
 * @property unlockAt UTC schedule instant required for TIME or TIME_AND_LOCATION locks, or null.
 * @property locationTarget Target location coordinates and 150m radius for LOCATION or TIME_AND_LOCATION locks, or null.
 */
data class RecipientUnlockRequirements(
    val capsuleId: String,
    val recipientUid: String,
    val unlocked: Boolean,
    val lockMode: LockMode,
    val unlockAt: Instant? = null,
    val locationTarget: LocationTarget? = null,
    val unlockedAt: Instant? = null
)
