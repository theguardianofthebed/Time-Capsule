package com.example.timecapsule.data.adapter

import android.content.Context
import com.example.timecapsule.config.FirebaseConfig
import com.example.timecapsule.domain.model.Availability
import com.example.timecapsule.domain.model.RecipientProfile
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

    override suspend fun verifyRecipientUnlock(
        capsuleId: String,
        recipientUid: String
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
             * Atomic Firestore transaction for recipient unlock verification (Correction A).
             * Atomically reads, verifies, and updates the capsule document within [runTransaction]:
             * 1. Reads current capsule document inside transaction via [Transaction.get].
             * 2. Idempotency: Returns [TransactionUnlockResult.AlreadyUnlocked] if `unlocked == true`.
             * 3. Authorization: Verifies `recipientId == recipientUid`.
             * 4. Lock mode: Only `"TIME"` mode is permitted for first unlock in Issue #15 (`"LOCATION"`, `"BOTH"`,
             *    and `"TIME_AND_LOCATION"` fail-closed with Issue #17 notice).
             * 5. Atomicity & Server Timestamp: Updates `unlocked = true` and `unlockedAt = FieldValue.serverTimestamp()`
             *    inside the transaction.
             *
             * Firestore Transaction Retries & SDK Behavior:
             * Firestore SDK automatically retries the transaction if a concurrent edit occurs before commit.
             * Server timestamp evaluation and `request.time >= resource.data.unlockAt` deadline validation are
             * evaluated atomically on the server by `canRecipientUnlock()` in `firestore.rules`. Unconfirmed
             * local write overlays are never treated as successful remote authorization.
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
                if (lockModeStr != "TIME") {
                    return@runTransaction TransactionUnlockResult.UnsupportedMode(lockModeStr)
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
                is TransactionUnlockResult.UnsupportedMode -> {
                    if (result.mode == "LOCATION" || result.mode == "BOTH" || result.mode == "TIME_AND_LOCATION") {
                        OperationResult.Denied("Location and combined unlock conditions require Issue #17 implementation.")
                    } else {
                        OperationResult.Invalid("Unsupported lock mode: ${result.mode}")
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            try {
                val checkDoc = instance.collection("capsules").document(cleanCapsuleId).get(Source.SERVER).await()
                if (checkDoc.exists() &&
                    checkDoc.getString("recipientId") == cleanRecipientUid &&
                    checkDoc.getBoolean("unlocked") == true) {
                    return OperationResult.Success(Availability.AVAILABLE)
                }
            } catch (_: Exception) {
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
