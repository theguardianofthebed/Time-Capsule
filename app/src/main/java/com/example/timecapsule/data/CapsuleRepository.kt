package com.example.timecapsule.data

import com.example.timecapsule.domain.model.Availability
import com.example.timecapsule.domain.model.CapsuleDraft
import com.example.timecapsule.domain.model.CapsuleListsSnapshot
import com.example.timecapsule.domain.model.ProtectedCapsuleContent
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.model.SendStatus
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot

/**
 * Primary repository boundary for capsule operations, session isolation, and data access.
 * Enforces session generation checks and returns strongly typed [OperationResult] outcomes.
 */
interface CapsuleRepository {

    /** Returns the current active session snapshot. */
    fun session(): SessionSnapshot

    /** Performs exact recipient profile lookup by [uid]. */
    suspend fun getRecipient(uid: String): OperationResult<RecipientProfile>

    /** Submits an immutable frozen capsule publication [frozenOperation]. */
    suspend fun publish(frozenOperation: CapsuleDraft): OperationResult<String>

    /** Reconciles the authoritative outcome for [capsuleId]. */
    suspend fun reconcile(capsuleId: String): OperationResult<SendStatus>

    /** Retrieves participant-scoped Sent and Received capsule lists for active user [uid]. */
    suspend fun lists(uid: String): OperationResult<CapsuleListsSnapshot>

    /** Requests server-authoritative unlock verification for [capsuleId]. */
    suspend fun verifyUnlock(capsuleId: String): OperationResult<Availability>

    /** Reads protected content for [capsuleId] after authorization check. */
    suspend fun readContent(capsuleId: String): OperationResult<ProtectedCapsuleContent>
}
