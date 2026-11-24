package com.example.timecapsule.data.adapter

import android.content.Context
import com.example.timecapsule.config.FirebaseConfig
import com.example.timecapsule.domain.location.LocationQualifier
import com.example.timecapsule.domain.model.Availability
import com.example.timecapsule.domain.model.ForegroundLocationSample
import com.example.timecapsule.domain.model.LocationTarget
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.domain.model.QualificationFailureReason
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.model.RecipientUnlockRequirements
import com.example.timecapsule.domain.result.OperationResult
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

private sealed interface TransactionUnlockResult {
    object AlreadyUnlocked : TransactionUnlockResult
    object UnlockedSuccess : TransactionUnlockResult
    object NotFound : TransactionUnlockResult
    object NotRecipient : TransactionUnlockResult
    object LocationSampleMissing : TransactionUnlockResult
    object InvalidTargetLocation : TransactionUnlockResult
    data class LocationDisqualified(val reason: QualificationFailureReason) : TransactionUnlockResult
    data class UnsupportedMode(val mode: String) : TransactionUnlockResult
}

/**
 * Production implementation of [FirestoreAdapter] bound to [FirebaseConfig.getMemoryOnlyFirestore].
 * Enforces memory-only cache configuration before returning the Firestore SDK instance.
 */
class DefaultFirestoreAdapter(
    private val contextSupplier: () -> Context? = { null }
) : FirestoreAdapter {

    override fun isAvailable(): Boolean {
        val ctx = contextSupplier()
        return ctx != null && FirebaseConfig.isFirebaseAvailable(ctx)
    }

    override fun firestoreInstance(): FirebaseFirestore? {
        return FirebaseConfig.getMemoryOnlyFirestore(contextSupplier())
    }

    override suspend fun getRecipientProfile(uid: String): OperationResult<RecipientProfile> {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) {
            return OperationResult.Invalid("UID must not be blank for recipient lookup.")
        }

        val instance = firestoreInstance()
            ?: return OperationResult.Unavailable("Firestore service is unavailable.")

        return try {
            val doc = instance.collection("recipientDirectory")
                .document(cleanUid)
                .get()
                .await()

            if (doc.exists()) {
                val displayName = doc.getString("displayName") ?: ""
                OperationResult.Success(RecipientProfile(uid = cleanUid, displayName = displayName))
            } else {
                OperationResult.Invalid("No recipient found with UID: $cleanUid")
            }
        } catch (e: Exception) {
            mapFirestoreException(e)
        }
    }

    override suspend fun saveRecipientProfile(profile: RecipientProfile): OperationResult<Unit> {
        val cleanUid = profile.uid.trim()
        val cleanName = profile.displayName.trim()
        if (cleanUid.isBlank()) {
            return OperationResult.Invalid("User UID must not be blank when saving profile.")
        }

        val instance = firestoreInstance()
            ?: return OperationResult.Unavailable("Firestore service is unavailable.")

        val data = mapOf(
            "uid" to cleanUid,
            "displayName" to cleanName
        )

        return try {
            instance.collection("recipientDirectory")
                .document(cleanUid)
                .set(data)
                .await()
            OperationResult.Success(Unit)
        } catch (e: Exception) {
            mapFirestoreException(e)
        }
    }

    override suspend fun readRecipientUnlockRequirements(
        capsuleId: String,
        recipientUid: String
    ): OperationResult<RecipientUnlockRequirements> {
        val cleanCapsuleId = capsuleId.trim()
        val cleanRecipientUid = recipientUid.trim()
        if (cleanCapsuleId.isBlank() || cleanRecipientUid.isBlank()) {
            return OperationResult.Invalid("Capsule ID and recipient UID must not be blank.")
        }

        val instance = firestoreInstance()
            ?: return OperationResult.Unavailable("Firestore service is unavailable.")

        return try {
            val doc = instance.collection("capsules")
                .document(cleanCapsuleId)
                .get(Source.SERVER)
                .await()

            if (!doc.exists()) {
                return OperationResult.Invalid("Capsule not found with ID: $cleanCapsuleId")
            }

            val docRecipientId = doc.getString("recipientId")
            if (docRecipientId != cleanRecipientUid) {
                return OperationResult.Denied("Only the designated recipient can unlock this capsule.")
            }

            // P0: Reject entire unconfirmed/cached metadata snapshot.
            if (doc.metadata.hasPendingWrites() || doc.metadata.isFromCache) {
                return OperationResult.Unavailable("Capsule metadata is unconfirmed or cached.")
            }
            val unlocked = doc.getBoolean("unlocked") ?: false

            val lockModeStr = doc.getString("lockMode") ?: "TIME"
            val lockMode = when (lockModeStr) {
                "TIME" -> LockMode.TIME
                "LOCATION" -> LockMode.LOCATION
                "BOTH", "TIME_AND_LOCATION" -> LockMode.TIME_AND_LOCATION
                else -> return OperationResult.Invalid("Unsupported lock mode: $lockModeStr")
            }

            val unlockAtTimestamp = doc.getTimestamp("unlockAt")
            val unlockAtInstant = unlockAtTimestamp?.toInstant()

            val locMap = doc.get("location") as? Map<*, *>
            val locationTarget = if (locMap != null) {
                val lat = (locMap["latitude"] as? Number)?.toDouble()
                val lng = (locMap["longitude"] as? Number)?.toDouble()
                val radiusM = (locMap["radiusM"] as? Number)?.toDouble()
                if (lat != null && lng != null && radiusM != null && lat.isFinite() && lng.isFinite() && radiusM == 150.0) {
                    LocationTarget(latitude = lat, longitude = lng, radiusMeters = radiusM)
                } else {
                    if (lockMode == LockMode.LOCATION || lockMode == LockMode.TIME_AND_LOCATION) {
                        return OperationResult.Invalid("Capsule location target is malformed or invalid.")
                    }
                    null
                }
            } else {
                if (lockMode == LockMode.LOCATION || lockMode == LockMode.TIME_AND_LOCATION) {
                    return OperationResult.Invalid("Location lock mode requires a target location.")
                }
                null
            }

            OperationResult.Success(
                RecipientUnlockRequirements(
                    capsuleId = cleanCapsuleId,
                    recipientUid = cleanRecipientUid,
                    unlocked = unlocked,
                    lockMode = lockMode,
                    unlockAt = unlockAtInstant,
                    locationTarget = locationTarget
                )
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            mapFirestoreException(e)
        }
    }

    override suspend fun verifyRecipientUnlock(
        capsuleId: String,
        recipientUid: String,
        locationSample: ForegroundLocationSample?,
        clockNanosSupplier: () -> Long
    ): OperationResult<Availability> {
        val cleanCapsuleId = capsuleId.trim()
        val cleanRecipientUid = recipientUid.trim()
        if (cleanCapsuleId.isBlank() || cleanRecipientUid.isBlank()) {
            return OperationResult.Invalid("Capsule ID and recipient UID must not be blank.")
        }

        val instance = firestoreInstance()
            ?: return OperationResult.Unavailable("Firestore service is unavailable.")

        return try {
            val docRef = instance.collection("capsules").document(cleanCapsuleId)

            /**
             * Atomic Firestore transaction for recipient unlock verification.
             * Atomically reads, verifies, and updates the capsule document within [runTransaction]:
             * 1. Reads current capsule document inside transaction via [Transaction.get].
             * 2. Idempotency: Returns [TransactionUnlockResult.AlreadyUnlocked] if `unlocked == true`.
             * 3. Authorization: Verifies `recipientId == recipientUid`.
             * 4. For LOCATION/BOTH modes: Evaluates location sample quality, age (0..60s), accuracy (<=100m),
             *    and target distance (<=150m) using [LocationQualifier.qualify] with fresh [clockNanosSupplier]
             *    on EVERY callback execution retry.
             * 5. Atomicity & Server Timestamp: Updates `unlocked = true` and `unlockedAt = FieldValue.serverTimestamp()`
             *    inside the transaction. Server rules enforce `request.time >= resource.data.unlockAt` for TIME/BOTH.
             */
            val result = instance.runTransaction<TransactionUnlockResult> { transaction ->
                val snapshot = transaction.get(docRef)
                if (!snapshot.exists()) {
                    return@runTransaction TransactionUnlockResult.NotFound
                }

                val docRecipientId = snapshot.getString("recipientId")
                if (docRecipientId != cleanRecipientUid) {
                    return@runTransaction TransactionUnlockResult.NotRecipient
                }

                val unlocked = snapshot.getBoolean("unlocked") ?: false
                if (unlocked) {
                    return@runTransaction TransactionUnlockResult.AlreadyUnlocked
                }

                val lockModeStr = snapshot.getString("lockMode") ?: "TIME"
                val mode = when (lockModeStr) {
                    "TIME" -> LockMode.TIME
                    "LOCATION" -> LockMode.LOCATION
                    "BOTH", "TIME_AND_LOCATION" -> LockMode.TIME_AND_LOCATION
                    else -> return@runTransaction TransactionUnlockResult.UnsupportedMode(lockModeStr)
                }

                if (mode == LockMode.LOCATION || mode == LockMode.TIME_AND_LOCATION) {
                    if (locationSample == null) {
                        return@runTransaction TransactionUnlockResult.LocationSampleMissing
                    }

                    val locMap = snapshot.get("location") as? Map<*, *>
                    val lat = (locMap?.get("latitude") as? Number)?.toDouble()
                    val lng = (locMap?.get("longitude") as? Number)?.toDouble()
                    val radiusM = (locMap?.get("radiusM") as? Number)?.toDouble()

                    if (lat == null || lng == null || radiusM == null || !lat.isFinite() || !lng.isFinite() || radiusM != 150.0) {
                        return@runTransaction TransactionUnlockResult.InvalidTargetLocation
                    }

                    val target = LocationTarget(latitude = lat, longitude = lng, radiusMeters = radiusM)
                    val currentMonotonicNanos = clockNanosSupplier()
                    val qualification = LocationQualifier.qualify(target, locationSample, currentMonotonicNanos)

                    if (!qualification.isQualifying) {
                        return@runTransaction TransactionUnlockResult.LocationDisqualified(qualification.reason)
                    }
                }

                transaction.update(docRef, mapOf(
                    "unlocked" to true,
                    "unlockedAt" to FieldValue.serverTimestamp()
                ))

                TransactionUnlockResult.UnlockedSuccess
            }.await()

            when (result) {
                is TransactionUnlockResult.AlreadyUnlocked,
                is TransactionUnlockResult.UnlockedSuccess -> OperationResult.Success(Availability.AVAILABLE)
                is TransactionUnlockResult.NotFound -> OperationResult.Invalid("Capsule not found with ID: $cleanCapsuleId")
                is TransactionUnlockResult.NotRecipient -> OperationResult.Denied("Only the designated recipient can unlock this capsule.")
                is TransactionUnlockResult.LocationSampleMissing -> OperationResult.Denied("Location sample is required to unlock location-restricted capsule.")
                is TransactionUnlockResult.InvalidTargetLocation -> OperationResult.Invalid("Capsule location target is missing or malformed.")
                is TransactionUnlockResult.LocationDisqualified -> {
                    when (result.reason) {
                        QualificationFailureReason.OUTSIDE_RADIUS -> OperationResult.Denied("Current location is outside target radius.")
                        QualificationFailureReason.STALE_SAMPLE -> OperationResult.Unavailable("Location sample is stale. Please acquire a fresh location.")
                        QualificationFailureReason.POOR_ACCURACY -> OperationResult.Unavailable("Location sample accuracy is too poor (must be <= 100m).")
                        QualificationFailureReason.FUTURE_TIMESTAMP -> OperationResult.Unavailable("Location sample timestamp is invalid.")
                        QualificationFailureReason.INVALID_COORDINATES -> OperationResult.Invalid("Location sample contains invalid coordinates.")
                        QualificationFailureReason.INVALID_TARGET -> OperationResult.Invalid("Target location is invalid or does not have 150m radius.")
                        QualificationFailureReason.MISSING_SAMPLE -> OperationResult.Denied("Location sample is missing.")
                        QualificationFailureReason.NONE -> OperationResult.Denied("Location qualification failed.")
                    }
                }
                is TransactionUnlockResult.UnsupportedMode -> OperationResult.Invalid("Unsupported lock mode: ${result.mode}")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            try {
                val checkDoc = instance.collection("capsules").document(cleanCapsuleId).get(Source.SERVER).await()
                if (checkDoc.exists() &&
                    !checkDoc.metadata.hasPendingWrites() &&
                    !checkDoc.metadata.isFromCache &&
                    checkDoc.getString("recipientId") == cleanRecipientUid &&
                    checkDoc.getBoolean("unlocked") == true) {
                    return OperationResult.Success(Availability.AVAILABLE)
                }
            } catch (nested: Exception) {
                if (nested is CancellationException) throw nested
                // Fall back to original mapped exception if verification check fails
            }
            mapFirestoreUnlockException(e)
        }
    }

    private fun mapFirestoreUnlockException(e: Exception): OperationResult<Availability> {
        if (e is CancellationException) throw e
        return when (e) {
            is FirebaseNetworkException -> {
                OperationResult.Unavailable("Network unavailable during unlock verification.")
            }
            is FirebaseFirestoreException -> {
                when (e.code) {
                    FirebaseFirestoreException.Code.UNAVAILABLE,
                    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> {
                        OperationResult.Unavailable("Firestore service unavailable: ${e.message}")
                    }
                    FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                        OperationResult.Denied("Unlock authorization denied or unlock time not reached.")
                    }
                    else -> {
                        OperationResult.Denied("Firestore unlock failed: ${e.message}")
                    }
                }
            }
            else -> {
                OperationResult.Denied("Unlock verification failed: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    private fun <T> mapFirestoreException(e: Exception): OperationResult<T> {
        if (e is CancellationException) throw e
        return when (e) {
            is FirebaseNetworkException -> {
                OperationResult.Unavailable("Network unavailable during Firestore operation.")
            }
            is FirebaseFirestoreException -> {
                when (e.code) {
                    FirebaseFirestoreException.Code.UNAVAILABLE,
                    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> {
                        OperationResult.Unavailable("Firestore service unavailable: ${e.message}")
                    }
                    FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                        OperationResult.Denied("Firestore permission denied: ${e.message}")
                    }
                    else -> {
                        OperationResult.Denied("Firestore request failed: ${e.message}")
                    }
                }
            }
            else -> {
                OperationResult.Denied("Firestore request failed: ${e.localizedMessage ?: e.message}")
            }
        }
    }
}
