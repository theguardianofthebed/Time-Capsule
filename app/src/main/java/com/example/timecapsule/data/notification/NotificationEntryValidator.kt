package com.example.timecapsule.data.notification

import android.content.Intent
import com.example.timecapsule.data.CapsuleRepository
import com.example.timecapsule.data.session.SessionTracker
import com.example.timecapsule.domain.model.Availability
import com.example.timecapsule.domain.model.UnlockVerificationOutcome
import com.example.timecapsule.domain.result.OperationResult

/**
 * Result of validating an incoming notification tap entry intent.
 */
sealed interface NotificationEntryResult {
    /** Valid notification entry intent targeting [capsuleId] for authenticated recipient. */
    data class Valid(val capsuleId: String, val outcome: UnlockVerificationOutcome) : NotificationEntryResult

    /** Invalid or unauthorized notification entry intent with [reason]. */
    data class Invalid(val reason: String) : NotificationEntryResult
}

/**
 * Validates incoming notification tap intents against active session boundaries and recipient authorization.
 * Treats external incoming Intents as untrusted and revalidates recipient access authoritatively before navigation.
 *
 * @param repository Capsule repository for authoritative access verification.
 * @param sessionTracker Process-wide session tracker for active session validation.
 */
class NotificationEntryValidator(
    private val repository: CapsuleRepository,
    private val sessionTracker: SessionTracker
) {

    /**
     * Inspects and authoritatively validates [intent] triggered from a notification tap.
     *
     * @param intent The incoming Activity intent to inspect.
     * @return [NotificationEntryResult] confirming or rejecting the entry attempt.
     */
    suspend fun validateEntryIntent(intent: Intent?): NotificationEntryResult {
        if (intent == null) {
            return NotificationEntryResult.Invalid("Intent is null.")
        }
        val action = intent.action
        val accountUid = intent.getStringExtra(DefaultNotificationAdapter.EXTRA_ACCOUNT_UID)
        val capsuleId = intent.getStringExtra(DefaultNotificationAdapter.EXTRA_CAPSULE_ID)
        return validateEntryPayload(action, accountUid, capsuleId)
    }

    /**
     * Revalidates notification entry payload ([action], [accountUid], [capsuleId]) authoritatively.
     */
    suspend fun validateEntryPayload(
        action: String?,
        accountUid: String?,
        capsuleId: String?
    ): NotificationEntryResult {
        if (action != DefaultNotificationAdapter.ACTION_NOTIFICATION_TAP) {
            return NotificationEntryResult.Invalid("Intent is null or not a notification tap action.")
        }

        val cleanAccountUid = accountUid?.trim() ?: ""
        val cleanCapsuleId = capsuleId?.trim() ?: ""

        if (cleanAccountUid.isBlank() || cleanCapsuleId.isBlank()) {
            return NotificationEntryResult.Invalid("Notification intent payload missing account UID or capsule ID.")
        }

        val currentSession = sessionTracker.currentSession()
        if (!currentSession.isAuthenticated || currentSession.uid.isNullOrBlank() || !sessionTracker.isSessionValid(currentSession)) {
            return NotificationEntryResult.Invalid("No authenticated session available to process notification entry.")
        }

        if (currentSession.uid != cleanAccountUid) {
            return NotificationEntryResult.Invalid("Notification account UID does not match current active session UID.")
        }

        val outcomeResult = repository.checkUnlockStatus(cleanCapsuleId, currentSession)
        if (!sessionTracker.isSessionValid(currentSession) || sessionTracker.currentSession() != currentSession) {
            return NotificationEntryResult.Invalid("Account session changed during notification authorization recheck.")
        }

        return when (outcomeResult) {
            is OperationResult.Success -> {
                if (outcomeResult.data.availability == Availability.AVAILABLE) {
                    NotificationEntryResult.Valid(cleanCapsuleId, outcomeResult.data)
                } else {
                    NotificationEntryResult.Invalid("Capsule is not available or remains locked.")
                }
            }
            is OperationResult.Denied -> NotificationEntryResult.Invalid("Denied: ${outcomeResult.message}")
            is OperationResult.Invalid -> NotificationEntryResult.Invalid("Invalid: ${outcomeResult.message}")
            is OperationResult.Unavailable -> NotificationEntryResult.Invalid("Unavailable: ${outcomeResult.message}")
            is OperationResult.OutcomeUnknown -> NotificationEntryResult.Invalid("Outcome unknown: ${outcomeResult.message}")
        }
    }
}
