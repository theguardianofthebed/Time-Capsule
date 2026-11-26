package com.example.timecapsule.data.publication

import com.example.timecapsule.domain.model.FrozenCapsuleOperation
import com.example.timecapsule.domain.model.LockMode
import java.time.Instant

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
            require(!operation.isOpaque) { "Cannot create publication payload from an opaque recovery record." }
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

    /** Reads server-only publication evidence; unimplemented/unavailable checks never prove absence. */
    suspend fun inspect(operation: FrozenCapsuleOperation): PublicationInspection =
        PublicationInspection.UNAVAILABLE
}

/** Server evidence for the exact operation, distinct from a local pending-write snapshot. */
enum class PublicationInspection { MATCHING, ABSENT, CONFLICT, PENDING, UNAVAILABLE }

/** The SDK confirmed rejection of this attempt; older unresolved attempts may still exist. */
class PublicationRejectedException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** Compares actual immutable fields as well as their digest, without relying on hash equality alone. */
object PublicationRecordMatcher {
    /** Allows locked self-send recovery from metadata because the batch rules require paired content. */
    fun matches(
        operation: FrozenCapsuleOperation,
        metadata: Map<String, Any?>,
        content: Map<String, Any?>?
    ): Boolean {
        if (operation.isOpaque) return matchesOpaque(operation, metadata)
        if (!matchesMetadata(operation, metadata)) return false
        if (content == null) return operation.senderUid == operation.recipientUid
        return equalFields(CapsulePublicationPayload.from(operation).content, content)
    }

    /** Detects visible metadata conflicts before attempting a potentially protected content read. */
    fun matchesMetadata(operation: FrozenCapsuleOperation, metadata: Map<String, Any?>): Boolean {
        if (operation.isOpaque) return matchesOpaque(operation, metadata)
        val expected = CapsulePublicationPayload.from(operation)
        val mutable = setOf("createdAt", "unlocked", "unlockedAt")
        if (metadata["createdAt"] !is java.time.Instant || metadata["unlocked"] !is Boolean) return false
        return equalFields(expected.metadata.filterKeys { it !in mutable },
            metadata.filterKeys { it !in mutable })
    }

    /** Verifies server metadata against minimal opaque recovery evidence without checking scrubbed fields. */
    fun matchesOpaque(operation: FrozenCapsuleOperation, metadata: Map<String, Any?>): Boolean {
        if (!operation.isOpaque) return false
        if (metadata["createdAt"] !is Instant || metadata["unlocked"] !is Boolean) return false
        return metadata["capsuleId"] == operation.capsuleId &&
            metadata["senderId"] == operation.senderUid &&
            metadata["snapshotDigest"] == operation.snapshotDigest
    }

    /** Normalizes Firestore numeric representations, keeping exact field sets and other types. */
    private fun equalFields(expected: Map<*, *>, actual: Map<*, *>): Boolean =
        expected.keys == actual.keys && expected.all { (key, value) ->
            val other = actual[key]
            when {
                value is Map<*, *> && other is Map<*, *> -> equalFields(value, other)
                value is Number && other is Number -> value.toDouble() == other.toDouble()
                else -> value == other
            }
        }
}
