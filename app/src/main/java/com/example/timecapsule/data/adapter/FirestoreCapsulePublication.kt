package com.example.timecapsule.data.adapter

import com.example.timecapsule.data.publication.CapsulePublicationBackend
import com.example.timecapsule.data.publication.CapsulePublicationPayload
import com.example.timecapsule.data.publication.PublicationServerValue
import com.example.timecapsule.data.publication.PublicationInspection
import com.example.timecapsule.data.publication.PublicationRecordMatcher
import com.example.timecapsule.data.publication.PublicationRejectedException
import com.example.timecapsule.domain.model.FrozenCapsuleOperation
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import java.time.Instant
import kotlinx.coroutines.tasks.await

/** Atomic two-document publication using #11's create-only rules and the authenticated sender. */
class FirestoreCapsulePublication(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : CapsulePublicationBackend {
    /**
     * Returns only when Firestore's commit task receives the backend acknowledgment.
     * set() is constrained to creation by the deployed rules: protectedContent updates are denied.
     * Both writes share one batch, so an existing/conflicting document cannot be partially replaced.
     */
    override suspend fun publish(operation: FrozenCapsuleOperation) {
        if (auth.currentUser?.uid != operation.senderUid) throw SecurityException("Sender changed.")
        val payload = CapsulePublicationPayload.from(operation)
        val batch = firestore.batch()
        batch.set(firestore.collection("capsules").document(operation.capsuleId),
            payload.metadata.mapValues { sdkValue(it.value) })
        batch.set(firestore.collection("protectedContent").document(operation.capsuleId),
            payload.content.mapValues { sdkValue(it.value) })
        // Awaiting local snapshots or pending-write flags would not prove server acceptance.
        try {
            batch.commit().await()
        } catch (error: FirebaseFirestoreException) {
            if (error.code in setOf(FirebaseFirestoreException.Code.PERMISSION_DENIED,
                    FirebaseFirestoreException.Code.UNAUTHENTICATED,
                    FirebaseFirestoreException.Code.INVALID_ARGUMENT,
                    FirebaseFirestoreException.Code.FAILED_PRECONDITION,
                    FirebaseFirestoreException.Code.ALREADY_EXISTS)) {
                throw PublicationRejectedException("This publication attempt was rejected.", error)
            }
            throw error
        }
    }

    /** Reads authoritative records and rejects cache/pending overlays as publication evidence. */
    override suspend fun inspect(operation: FrozenCapsuleOperation): PublicationInspection {
        if (auth.currentUser?.uid != operation.senderUid) throw SecurityException("Sender changed.")
        val metadata = firestore.collection("capsules").document(operation.capsuleId)
            .get(Source.SERVER).await()
        if (metadata.metadata.isFromCache || metadata.metadata.hasPendingWrites()) {
            return PublicationInspection.PENDING
        }
        if (!metadata.exists()) return PublicationInspection.ABSENT
        val fields = metadata.data?.mapValues { decodedValue(it.value) }
            ?: return PublicationInspection.CONFLICT
        if (operation.isOpaque) {
            return if (PublicationRecordMatcher.matchesOpaque(operation, fields))
                PublicationInspection.MATCHING else PublicationInspection.CONFLICT
        }
        if (!PublicationRecordMatcher.matchesMetadata(operation, fields)) {
            return PublicationInspection.CONFLICT
        }
        // Locked self-sends cannot read protected content; matched metadata plus paired-create
        // rules identifies that operation without weakening the protected-read authorization.
        val content = if (operation.senderUid == operation.recipientUid) null else {
            val snapshot = firestore.collection("protectedContent").document(operation.capsuleId)
                .get(Source.SERVER).await()
            if (snapshot.metadata.isFromCache || snapshot.metadata.hasPendingWrites()) {
                return PublicationInspection.PENDING
            }
            if (!snapshot.exists()) return PublicationInspection.CONFLICT
            snapshot.data?.mapValues { decodedValue(it.value) }
                ?: return PublicationInspection.CONFLICT
        }
        return if (PublicationRecordMatcher.matches(operation, fields, content))
            PublicationInspection.MATCHING else PublicationInspection.CONFLICT
    }

    /** Converts server timestamps and nested maps to the immutable comparison representation. */
    private fun decodedValue(value: Any?): Any? = when (value) {
        is Timestamp -> Instant.ofEpochSecond(value.seconds, value.nanoseconds.toLong())
        is Map<*, *> -> value.mapValues { decodedValue(it.value) }
        else -> value
    }

    /** Converts UTC values and the explicit server timestamp marker to supported SDK values. */
    private fun sdkValue(value: Any?): Any? = when (value) {
        is Instant -> Timestamp(value.epochSecond, value.nano)
        PublicationServerValue.TIMESTAMP -> FieldValue.serverTimestamp()
        else -> value
    }
}
