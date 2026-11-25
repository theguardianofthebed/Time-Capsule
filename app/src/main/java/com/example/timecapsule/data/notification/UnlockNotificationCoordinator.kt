package com.example.timecapsule.data.notification

import com.example.timecapsule.data.session.SessionTracker
import com.example.timecapsule.domain.model.UnlockConfirmationKind
import com.example.timecapsule.domain.model.UnlockVerificationOutcome
import com.example.timecapsule.domain.session.SessionSnapshot

/**
 * Coordinator orchestrating account-safe local notifications after confirmed recipient unlock.
 *
 * Decision Table & Security Enforcement:
 * 1. Confirmed First Unlock (Allowed): Atomically claims attempt and posts local alert.
 * 2. Confirmed First Unlock (Denied): Persists candidate in [AttemptState.PENDING_PERMISSION].
 * 3. Already Unlocked (Pending Permission & Allowed): Attempts once for pending candidate.
 * 4. Already Unlocked (Absent): Does NOT post historical alerts for already unlocked capsules.
 * 5. Session Protection: Revalidates immutable [SessionSnapshot] before attempt claim and posting.
 * 6. Exception Isolation: Notification or storage failures are caught and isolated so unlock verification
 *    results are never corrupted.
 *
 * @param notificationAdapter Platform adapter for OS notification permissions and posting.
 * @param attemptStore Persistent marker store for account/capsule attempt deduplication.
 * @param sessionTracker Process-wide session tracker for active session validation.
 */
class UnlockNotificationCoordinator(
    private val notificationAdapter: NotificationAdapter,
    private val attemptStore: NotificationAttemptStore,
    private val sessionTracker: SessionTracker
) {

    /**
     * Evaluates unlock confirmation [outcome] for [capsuleId] bound to [targetSession]
     * and attempts local notification posting if permitted and eligible.
     *
     * @param capsuleId The verified recipient capsule ID.
     * @param outcome The detailed unlock verification outcome.
     * @param targetSession Immutable session snapshot captured when verification started.
     */
    fun handleUnlockOutcome(
        capsuleId: String,
        outcome: UnlockVerificationOutcome,
        targetSession: SessionSnapshot
    ) {
        try {
            val cleanCapsuleId = capsuleId.trim()
            if (cleanCapsuleId.isBlank()) return

            if (!sessionTracker.isSessionValid(targetSession) || !targetSession.isAuthenticated || targetSession.uid.isNullOrBlank()) {
                return
            }
            val accountUid = targetSession.uid

            val currentState = attemptStore.getAttemptState(accountUid, cleanCapsuleId)

            when (outcome.confirmationKind) {
                UnlockConfirmationKind.FIRST_UNLOCK_CONFIRMED -> {
                    if (currentState == AttemptState.ATTEMPTED) return

                    if (!notificationAdapter.areNotificationsPermitted()) {
                        attemptStore.recordPendingPermission(accountUid, cleanCapsuleId)
                        return
                    }

                    if (!attemptStore.atomicallyClaimAttempt(accountUid, cleanCapsuleId)) return

                    if (!sessionTracker.isSessionValid(targetSession)) return

                    notificationAdapter.postUnlockNotification(accountUid, cleanCapsuleId)
                }
                UnlockConfirmationKind.ALREADY_UNLOCKED,
                UnlockConfirmationKind.RECONCILED_AVAILABLE -> {
                    if (currentState == AttemptState.PENDING_PERMISSION) {
                        if (!notificationAdapter.areNotificationsPermitted()) return

                        if (!attemptStore.atomicallyClaimAttempt(accountUid, cleanCapsuleId)) return

                        if (!sessionTracker.isSessionValid(targetSession)) return

                        notificationAdapter.postUnlockNotification(accountUid, cleanCapsuleId)
                    }
                }
            }
        } catch (_: Exception) {
            // Isolates notification exceptions from corrupting unlock verification outcomes
        }
    }
}
