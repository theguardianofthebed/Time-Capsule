package com.example.timecapsule.data

import com.example.timecapsule.data.adapter.AuthAdapter
import com.example.timecapsule.data.adapter.FirestoreAdapter
import com.example.timecapsule.data.publication.CapsulePublicationCoordinator
import com.example.timecapsule.data.session.SessionTracker
import com.example.timecapsule.domain.model.Availability
import com.example.timecapsule.domain.model.CapsuleDraft
import com.example.timecapsule.domain.model.CapsuleListsSnapshot
import com.example.timecapsule.domain.model.ProtectedCapsuleContent
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.model.SendStatus
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot

/**
 * Default implementation of [CapsuleRepository] interacting with platform adapters and session tracker.
 */
class DefaultCapsuleRepository(
    private val sessionTracker: SessionTracker,
    private val authAdapter: AuthAdapter,
    private val firestoreAdapter: FirestoreAdapter,
    private val publicationCoordinator: CapsulePublicationCoordinator? = null
) : CapsuleRepository {

    override fun session(): SessionSnapshot = sessionTracker.currentSession()

    override suspend fun getRecipient(uid: String): OperationResult<RecipientProfile> {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) {
            return OperationResult.Invalid("Recipient UID must not be blank.")
        }
        return firestoreAdapter.getRecipientProfile(cleanUid)
    }

    /** Persists and submits through the shared publication coordinator; success requires server ACK. */
    override suspend fun publish(frozenOperation: CapsuleDraft): OperationResult<String> =
        publicationCoordinator?.publish(frozenOperation)
            ?: OperationResult.Unavailable("Capsule publication service is unavailable.")

    /** Resolves server-only matching evidence without treating a missing record as a failed write. */
    override suspend fun reconcile(capsuleId: String): OperationResult<SendStatus> =
        publicationCoordinator?.reconcile(capsuleId)
            ?: OperationResult.Unavailable("Publication reconciliation is unavailable.")

    /** Replays the original saved snapshot only after the coordinator's server check. */
    override suspend fun retryPublication(capsuleId: String): OperationResult<String> =
        publicationCoordinator?.retry(capsuleId)
            ?: OperationResult.Unavailable("Publication retry is unavailable.")

    override suspend fun lists(uid: String): OperationResult<CapsuleListsSnapshot> {
        return OperationResult.Unavailable("Capsule lists query requires Issue #21 implementation.")
    }

    override suspend fun verifyUnlock(capsuleId: String): OperationResult<Availability> {
        return OperationResult.Invalid("Verification requires Issue #15 implementation.")
    }

    override suspend fun readContent(capsuleId: String): OperationResult<ProtectedCapsuleContent> {
        return OperationResult.Invalid("Protected content read requires Issue #21 implementation.")
    }
}
