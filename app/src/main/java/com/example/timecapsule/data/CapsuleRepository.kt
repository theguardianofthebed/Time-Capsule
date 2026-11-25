package com.example.timecapsule.data

import com.example.timecapsule.domain.model.Availability
import com.example.timecapsule.domain.model.CapsuleDraft
import com.example.timecapsule.domain.model.CapsuleListsSnapshot
import com.example.timecapsule.domain.model.ProtectedCapsuleContent
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.model.RecoveredPublication
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

    /** Reconciles the authoritative outcome for [capsuleId] using default current session. */
    suspend fun reconcile(capsuleId: String): OperationResult<SendStatus>

    /** Reconciles the authoritative outcome for [capsuleId] bound to [targetSession]. */
    suspend fun reconcile(capsuleId: String, targetSession: SessionSnapshot): OperationResult<SendStatus> = reconcile(capsuleId)

    /** Checks the server and retries only the same durably saved publication identity/content. */
    suspend fun retryPublication(capsuleId: String): OperationResult<String> =
        OperationResult.Unavailable("Publication retry is unavailable.")

    /** Restores account-scoped saved outcomes without automatically sending again using default current session. */
    suspend fun recoverPublications(): OperationResult<List<RecoveredPublication>> =
        OperationResult.Unavailable("Publication recovery is unavailable.")

    /** Restores account-scoped saved outcomes bound to [targetSession]. */
    suspend fun recoverPublications(targetSession: SessionSnapshot): OperationResult<List<RecoveredPublication>> = recoverPublications()

    /** Creates a new editable identity only after definite unpublished failure. */
    suspend fun abandonPublication(capsuleId: String): OperationResult<CapsuleDraft> =
        OperationResult.Unavailable("Publication replacement is unavailable.")

    /** Retrieves participant-scoped Sent and Received capsule lists for active user [uid]. */
    suspend fun lists(uid: String): OperationResult<CapsuleListsSnapshot>

    /** Requests server-authoritative unlock verification for [capsuleId] using default current session. */
    suspend fun verifyUnlock(capsuleId: String): OperationResult<Availability>

    /** Requests server-authoritative unlock verification for [capsuleId] bound to [targetSession]. */
    suspend fun verifyUnlock(capsuleId: String, targetSession: SessionSnapshot): OperationResult<Availability> = verifyUnlock(capsuleId)

    /** Reads protected content for [capsuleId] after authorization check. */
    suspend fun readContent(capsuleId: String): OperationResult<ProtectedCapsuleContent>
}
