package com.example.timecapsule.data.publication

import com.example.timecapsule.domain.model.FrozenCapsuleOperation
import com.example.timecapsule.domain.model.LockMode

/** Explicit server-assigned values; client time must never substitute for creation acknowledgment. */
enum class PublicationServerValue { TIMESTAMP }

/** Both immutable documents that must be committed together under the existing Firestore rules. */
data class CapsulePublicationPayload(
    val metadata: Map<String, Any?>,
    val content: Map<String, Any?>
) {
    companion object {
        /** Converts a validated snapshot into the shared #11 schema without changing its identity. */
        fun from(operation: FrozenCapsuleOperation): CapsulePublicationPayload {
            operation.verify()
            val metadata = linkedMapOf<String, Any?>(
                "capsuleId" to operation.capsuleId,
                "senderId" to operation.senderUid,
                "recipientId" to operation.recipientUid,
                "lockMode" to when (operation.lock.mode) {
                    LockMode.TIME -> "TIME"
                    LockMode.LOCATION -> "LOCATION"
                    LockMode.TIME_AND_LOCATION -> "BOTH"
                },
                "unlockAt" to operation.lock.unlockAt,
                "location" to operation.lock.location?.let {
                    mapOf("latitude" to it.latitude, "longitude" to it.longitude,
                        "radiusM" to it.radiusMeters)
                },
                "hasPhoto" to (operation.photo != null),
                "photoPath" to operation.photoObjectPath,
                "createdAt" to PublicationServerValue.TIMESTAMP,
                "creationZone" to operation.creationZone,
                "snapshotDigest" to operation.snapshotDigest,
                "unlocked" to false,
                "unlockedAt" to null
            )
            operation.title?.let { metadata["title"] = it }
            return CapsulePublicationPayload(metadata, mapOf(
                "capsuleId" to operation.capsuleId,
                "text" to operation.text,
                "photoObjectPath" to operation.photoObjectPath,
                "snapshotDigest" to operation.snapshotDigest
            ))
        }
    }
}

/** Publication boundary whose completion means final server acknowledgment, never local queuing. */
fun interface CapsulePublicationBackend {
    /** Commits both documents atomically; a thrown error may still require server reconciliation. */
    suspend fun publish(operation: FrozenCapsuleOperation)
}
