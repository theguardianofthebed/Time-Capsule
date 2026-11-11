package com.example.timecapsule.data.adapter

import android.content.Context
import com.example.timecapsule.config.FirebaseConfig
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.result.OperationResult
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
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
