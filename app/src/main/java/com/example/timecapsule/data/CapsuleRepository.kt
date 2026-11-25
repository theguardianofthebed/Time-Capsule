package com.example.timecapsule.data

import com.example.timecapsule.domain.model.Availability
import com.example.timecapsule.domain.model.CapsuleDraft
import com.example.timecapsule.domain.model.CapsuleListsSnapshot
import com.example.timecapsule.domain.model.ProtectedCapsuleContent
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.model.RecoveredPublication
import com.example.timecapsule.domain.model.SendStatus
import com.example.timecapsule.domain.model.UnlockConfirmationKind
import com.example.timecapsule.domain.model.UnlockVerificationOutcome
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

    /** Submits an immutable frozen capsule publication [frozenOperation] bound to [targetSession]. */
    suspend fun publish(frozenOperation: CapsuleDraft, targetSession: SessionSnapshot): OperationResult<String> =
        if (session() != targetSession) OperationResult.Denied("Account session changed.")
        else publish(frozenOperation)

    /** Reconciles the authoritative outcome for [capsuleId]. */
    suspend fun reconcile(capsuleId: String): OperationResult<SendStatus>

    /** Reconciles the authoritative outcome for [capsuleId] bound to [targetSession]. */
    suspend fun reconcile(capsuleId: String, targetSession: SessionSnapshot): OperationResult<SendStatus> =
        if (session() != targetSession) OperationResult.Denied("Account session changed.")
        else reconcile(capsuleId)

    /** Checks the server and retries only the same durably saved publication identity/content. */
    suspend fun retryPublication(capsuleId: String): OperationResult<String> =
        OperationResult.Unavailable("Publication retry is unavailable.")

    /** Checks the server and retries only the same durably saved publication identity/content bound to [targetSession]. */
    suspend fun retryPublication(capsuleId: String, targetSession: SessionSnapshot): OperationResult<String> =
        if (session() != targetSession) OperationResult.Denied("Account session changed.")
        else retryPublication(capsuleId)

    /** Restores account-scoped saved outcomes without automatically sending again. */
    suspend fun recoverPublications(): OperationResult<List<RecoveredPublication>> =
        OperationResult.Unavailable("Publication recovery is unavailable.")

    /** Restores account-scoped saved outcomes bound to [targetSession]. */
    suspend fun recoverPublications(targetSession: SessionSnapshot): OperationResult<List<RecoveredPublication>> =
        if (session() != targetSession) OperationResult.Denied("Account session changed.")
        else recoverPublications()

    /** Creates a new editable identity only after definite unpublished failure. */
    suspend fun abandonPublication(capsuleId: String): OperationResult<CapsuleDraft> =
        OperationResult.Unavailable("Publication replacement is unavailable.")

    /** Creates a new editable identity bound to [targetSession]. */
    suspend fun abandonPublication(capsuleId: String, targetSession: SessionSnapshot): OperationResult<CapsuleDraft> =
        if (session() != targetSession) OperationResult.Denied("Account session changed.")
        else abandonPublication(capsuleId)

    /** Retrieves participant-scoped Sent and Received capsule lists for active user [uid]. */
    suspend fun lists(uid: String): OperationResult<CapsuleListsSnapshot>

    /** Requests server-authoritative unlock verification for [capsuleId] with detailed outcome. */
    suspend fun verifyUnlockOutcome(capsuleId: String): OperationResult<UnlockVerificationOutcome> =
        verifyUnlockOutcome(capsuleId, session())

    /** Requests server-authoritative unlock verification for [capsuleId] bound to [targetSession] with detailed outcome. */
    suspend fun verifyUnlockOutcome(capsuleId: String, targetSession: SessionSnapshot): OperationResult<UnlockVerificationOutcome> {
        if (session() != targetSession) return OperationResult.Denied("Account session changed.")
        return when (val res = verifyUnlock(capsuleId, targetSession)) {
            is OperationResult.Success -> OperationResult.Success(
                UnlockVerificationOutcome(res.data, UnlockConfirmationKind.FIRST_UNLOCK_CONFIRMED)
            )
            is OperationResult.Invalid -> OperationResult.Invalid(res.message)
            is OperationResult.Denied -> OperationResult.Denied(res.message)
            is OperationResult.Unavailable -> OperationResult.Unavailable(res.message)
            is OperationResult.OutcomeUnknown -> OperationResult.OutcomeUnknown(res.capsuleId, res.message)
        }
    }

    /** Requests server-authoritative unlock verification for [capsuleId]. */
    suspend fun verifyUnlock(capsuleId: String): OperationResult<Availability> =
        verifyUnlock(capsuleId, session())

    /** Requests server-authoritative unlock verification for [capsuleId] bound to [targetSession]. */
    suspend fun verifyUnlock(capsuleId: String, targetSession: SessionSnapshot): OperationResult<Availability> =
        if (session() != targetSession) OperationResult.Denied("Account session changed.")
        else verifyUnlock(capsuleId)

    /** Reads protected content for [capsuleId] after authorization check. */
    suspend fun readContent(capsuleId: String): OperationResult<ProtectedCapsuleContent>
}
