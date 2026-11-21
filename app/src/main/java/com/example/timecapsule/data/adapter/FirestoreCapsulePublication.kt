package com.example.timecapsule.data.adapter

import com.example.timecapsule.data.publication.CapsulePublicationBackend
import com.example.timecapsule.data.publication.CapsulePublicationPayload
import com.example.timecapsule.data.publication.PublicationServerValue
import com.example.timecapsule.domain.model.FrozenCapsuleOperation
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
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
        batch.commit().await()
    }

    /** Converts UTC values and the explicit server timestamp marker to supported SDK values. */
    private fun sdkValue(value: Any?): Any? = when (value) {
        is Instant -> Timestamp(value.epochSecond, value.nano)
        PublicationServerValue.TIMESTAMP -> FieldValue.serverTimestamp()
        else -> value
    }
}
