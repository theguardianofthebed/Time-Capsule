package com.example.timecapsule.domain.model

/**
 * Kind of confirmation established during recipient unlock verification.
 * Distinguishes newly acknowledged first-unlock transitions from previously unlocked metadata.
 */
enum class UnlockConfirmationKind {
    /**
     * First transition from locked to unlocked was executed and confirmed by this transaction.
     */
    FIRST_UNLOCK_CONFIRMED,

    /**
     * Capsule was already unlocked prior to this verification request.
     */
    ALREADY_UNLOCKED,

    /**
     * Unlock status was reconciled as available following transaction exception recovery.
     */
    RECONCILED_AVAILABLE
}

/**
 * Strongly typed outcome of recipient unlock verification combining [availability] status
 * and [confirmationKind] to establish notification eligibility.
 *
 * @property availability Availability status of the verified capsule.
 * @property confirmationKind Specific confirmation kind establishing first-unlock vs already-unlocked provenance.
 */
data class UnlockVerificationOutcome(
    val availability: Availability,
    val confirmationKind: UnlockConfirmationKind
)
