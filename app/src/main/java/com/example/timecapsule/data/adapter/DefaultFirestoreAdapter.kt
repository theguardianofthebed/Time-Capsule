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
import kotlinx.coroutines.tasks.await

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
            val snapshot = docRef.get(Source.SERVER).await()

            if (!snapshot.exists()) {
                return OperationResult.Invalid("Capsule not found with ID: $cleanCapsuleId")
            }

            val docRecipientId = snapshot.getString("recipientId")
            if (docRecipientId != cleanRecipientUid) {
                return OperationResult.Denied("Only the designated recipient can unlock this capsule.")
            }

            val unlocked = snapshot.getBoolean("unlocked") ?: false
            if (unlocked) {
                // Server-confirmed unlocked state is idempotent success (DD-17/DD-23)
                return OperationResult.Success(Availability.AVAILABLE)
            }

            val lockModeStr = snapshot.getString("lockMode") ?: "TIME"

            when (lockModeStr) {
                "TIME" -> {
                    // Update unlocked=true with serverTimestamp() as required by canRecipientUnlock() in firestore.rules
                    val updateData = mapOf(
                        "unlocked" to true,
                        "unlockedAt" to FieldValue.serverTimestamp()
                    )
                    docRef.update(updateData).await()
                    OperationResult.Success(Availability.AVAILABLE)
                }
                "LOCATION" -> {
                    OperationResult.Denied("Location unlock condition requires Issue #17 implementation.")
                }
                "TIME_AND_LOCATION" -> {
                    OperationResult.Denied("Combined time and location unlock condition requires Issue #17 implementation.")
                }
                else -> {
                    OperationResult.Invalid("Unsupported lock mode: $lockModeStr")
                }
            }
        } catch (e: Exception) {
            mapFirestoreUnlockException(e)
        }
    }

    private fun mapFirestoreUnlockException(e: Exception): OperationResult<Availability> {
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
