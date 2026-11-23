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

    /** Restores private operations through server reconciliation without implicit replay. */
    override suspend fun recoverPublications() =
        publicationCoordinator?.recoverPublications()
            ?: OperationResult.Unavailable("Publication recovery is unavailable.")

    /** Retains failed content under a new identity only when safe abandonment is established. */
    override suspend fun abandonPublication(capsuleId: String) =
        publicationCoordinator?.abandonForNewDraft(capsuleId)
            ?: OperationResult.Unavailable("Publication replacement is unavailable.")

    override suspend fun lists(uid: String): OperationResult<CapsuleListsSnapshot> {
        return OperationResult.Unavailable("Capsule lists query requires Issue #21 implementation.")
    }

    override suspend fun verifyUnlock(capsuleId: String): OperationResult<Availability> {
        val cleanId = capsuleId.trim()
        if (cleanId.isBlank()) {
            return OperationResult.Invalid("Capsule ID must not be blank for unlock verification.")
        }
        val initialSession = sessionTracker.currentSession()
        if (!initialSession.isAuthenticated || initialSession.uid.isNullOrBlank()) {
            return OperationResult.Denied("Authentication required to verify capsule unlock.")
        }
        val activeUid = initialSession.uid
        val result = firestoreAdapter.verifyRecipientUnlock(cleanId, activeUid)
        val postSession = sessionTracker.currentSession()
        if (!postSession.isAuthenticated || postSession != initialSession) {
            return OperationResult.Denied("Account session changed during unlock verification.")
        }
        return result
    }

    override suspend fun readContent(capsuleId: String): OperationResult<ProtectedCapsuleContent> {
        return OperationResult.Invalid("Protected content read requires Issue #21 implementation.")
    }
}
