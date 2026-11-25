package com.example.timecapsule.data.adapter

import android.os.SystemClock
import com.example.timecapsule.domain.model.Availability
import com.example.timecapsule.domain.model.ForegroundLocationSample
import com.example.timecapsule.domain.model.ProtectedCapsuleContent
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.model.RecipientUnlockRequirements
import com.example.timecapsule.domain.model.UnlockConfirmationKind
import com.example.timecapsule.domain.model.UnlockVerificationOutcome
import com.example.timecapsule.domain.result.OperationResult
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings

/**
 * Configures [FirebaseFirestore] memory-only cache settings as required by NFR-01 and ARC-06.
 * Disables persistent disk cache to prevent sensitive capsule data surviving session cleanup.
 */
object FirestoreCachePolicy {

    /** Creates memory-only cache settings instance without persistent disk caching. */
    fun createMemoryOnlySettings(): FirebaseFirestoreSettings {
        return FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
            .build()
    }

    /** Applies memory-only cache settings to [firestore] instance before first use. */
    fun applyMemoryOnlyCache(firestore: FirebaseFirestore): FirebaseFirestoreSettings {
        val settings = createMemoryOnlySettings()
        firestore.firestoreSettings = settings
        return settings
    }
}

/**
 * Replaceable adapter boundary for Firestore remote capsule and recipient metadata operations.
 */
interface FirestoreAdapter {
    /** True if Firestore adapter is configured and ready. */
    fun isAvailable(): Boolean

    /** Returns the configured memory-only [FirebaseFirestore] instance or null if unavailable. */
    fun firestoreInstance(): FirebaseFirestore?

    /** Performs exact recipient profile lookup in `recipientDirectory/{uid}`. */
    suspend fun getRecipientProfile(uid: String): OperationResult<RecipientProfile>

    /** Creates or updates public recipient profile in `recipientDirectory/{uid}`. */
    suspend fun saveRecipientProfile(profile: RecipientProfile): OperationResult<Unit>

    /** Reads authoritative server-only recipient unlock requirements for [capsuleId]. */
    suspend fun readRecipientUnlockRequirements(
        capsuleId: String,
        recipientUid: String
    ): OperationResult<RecipientUnlockRequirements> {
        return OperationResult.Unavailable("Unlock requirements lookup is not implemented in this adapter.")
    }

    /** Requests server-authoritative recipient unlock verification for [capsuleId] with detailed outcome. */
    suspend fun verifyRecipientUnlockDetailed(
        capsuleId: String,
        recipientUid: String,
        locationSample: ForegroundLocationSample? = null,
        clockNanosSupplier: () -> Long = { SystemClock.elapsedRealtimeNanos() }
    ): OperationResult<UnlockVerificationOutcome> {
        return when (val result = verifyRecipientUnlock(capsuleId, recipientUid, locationSample, clockNanosSupplier)) {
            is OperationResult.Success -> OperationResult.Success(
                UnlockVerificationOutcome(result.data, UnlockConfirmationKind.ALREADY_UNLOCKED)
            )
            is OperationResult.Invalid -> OperationResult.Invalid(result.message)
            is OperationResult.Denied -> OperationResult.Denied(result.message)
            is OperationResult.Unavailable -> OperationResult.Unavailable(result.message)
            is OperationResult.OutcomeUnknown -> OperationResult.OutcomeUnknown(result.capsuleId, result.message)
        }
    }

    /** Requests server-authoritative recipient unlock verification for [capsuleId]. */
    suspend fun verifyRecipientUnlock(
        capsuleId: String,
        recipientUid: String,
        locationSample: ForegroundLocationSample? = null,
        clockNanosSupplier: () -> Long = { SystemClock.elapsedRealtimeNanos() }
    ): OperationResult<Availability> {
        return OperationResult.Unavailable("Unlock verification is not implemented in this adapter.")
    }

    /** Reads protected content for [capsuleId] if recipient is authorized. */
    suspend fun readProtectedContent(
        capsuleId: String,
        recipientUid: String
    ): OperationResult<ProtectedCapsuleContent> {
        return OperationResult.Unavailable("Protected content read is not implemented in this adapter.")
    }
}
