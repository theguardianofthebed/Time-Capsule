package com.example.timecapsule.data

import com.example.timecapsule.data.adapter.AuthAdapter
import com.example.timecapsule.data.adapter.FirestoreAdapter
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
    private val firestoreAdapter: FirestoreAdapter
) : CapsuleRepository {

    override fun session(): SessionSnapshot = sessionTracker.currentSession()

    override suspend fun getRecipient(uid: String): OperationResult<RecipientProfile> {
        return firestoreAdapter.getRecipientProfile(uid)
    }

    override suspend fun publish(frozenOperation: CapsuleDraft): OperationResult<String> {
        return OperationResult.Invalid("Publication requires Issue #14 implementation.")
    }

    override suspend fun reconcile(capsuleId: String): OperationResult<SendStatus> {
        return OperationResult.Invalid("Reconciliation requires Issue #14 implementation.")
    }

    override suspend fun lists(uid: String): OperationResult<CapsuleListsSnapshot> {
        return OperationResult.Success(CapsuleListsSnapshot(emptyList(), emptyList()))
    }

    override suspend fun verifyUnlock(capsuleId: String): OperationResult<Availability> {
        return OperationResult.Invalid("Verification requires Issue #15 implementation.")
    }

    override suspend fun readContent(capsuleId: String): OperationResult<ProtectedCapsuleContent> {
        return OperationResult.Invalid("Protected content read requires Issue #21 implementation.")
    }
}
