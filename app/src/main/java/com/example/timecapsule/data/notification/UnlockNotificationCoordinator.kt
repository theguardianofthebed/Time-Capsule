package com.example.timecapsule.data.notification

import com.example.timecapsule.data.session.SessionTracker
import com.example.timecapsule.domain.model.Availability
import com.example.timecapsule.domain.model.UnlockConfirmationKind
import com.example.timecapsule.domain.model.UnlockVerificationOutcome
import com.example.timecapsule.domain.session.SessionSnapshot

/**
 * Coordinator orchestrating account-safe local notifications after confirmed recipient unlock.
 *
 * Decision Table & Security Enforcement:
 * 1. Confirmed First Unlock (Allowed): Posts local alert and atomically claims attempt on posting.
 * 2. Confirmed First Unlock (Denied/Blocked): Preserves candidate in [AttemptState.PENDING_PERMISSION].
 * 3. Already Unlocked (Pending Permission & Allowed): Attempts once for pending candidate.
 * 4. Already Unlocked (Absent): Does NOT post historical alerts for already unlocked capsules.
 * 5. Session Drift Protection: Revalidates immutable [SessionSnapshot] before attempt claim and posting.
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

    private val lock = Any()

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

            if (outcome.availability != Availability.AVAILABLE) {
                return
            }

            synchronized(lock) {
                if (!sessionTracker.isSessionValid(targetSession) ||
                    !targetSession.isAuthenticated ||
                    targetSession.uid.isNullOrBlank() ||
                    sessionTracker.currentSession() != targetSession) {
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

                        if (!sessionTracker.isSessionValid(targetSession) || sessionTracker.currentSession() != targetSession) {
                            return
                        }

                        val postResult = notificationAdapter.postUnlockNotification(accountUid, cleanCapsuleId)
                        when (postResult) {
                            NotificationPostResult.NOT_ATTEMPTED_PERMISSION_DENIED,
                            NotificationPostResult.NOT_ATTEMPTED_CHANNEL_BLOCKED -> {
                                attemptStore.recordPendingPermission(accountUid, cleanCapsuleId)
                            }
                            NotificationPostResult.ATTEMPTED_SUCCESS,
                            NotificationPostResult.ATTEMPTED_FAILED -> {
                                attemptStore.atomicallyClaimAttempt(accountUid, cleanCapsuleId)
                            }
                        }
                    }
                    UnlockConfirmationKind.ALREADY_UNLOCKED,
                    UnlockConfirmationKind.RECONCILED_AVAILABLE -> {
                        if (currentState == AttemptState.PENDING_PERMISSION) {
                            if (!notificationAdapter.areNotificationsPermitted()) return

                            if (!sessionTracker.isSessionValid(targetSession) || sessionTracker.currentSession() != targetSession) {
                                return
                            }

                            val postResult = notificationAdapter.postUnlockNotification(accountUid, cleanCapsuleId)
                            when (postResult) {
                                NotificationPostResult.NOT_ATTEMPTED_PERMISSION_DENIED,
                                NotificationPostResult.NOT_ATTEMPTED_CHANNEL_BLOCKED -> {
                                    // Remain PENDING_PERMISSION for retry
                                }
                                NotificationPostResult.ATTEMPTED_SUCCESS,
                                NotificationPostResult.ATTEMPTED_FAILED -> {
                                    attemptStore.atomicallyClaimAttempt(accountUid, cleanCapsuleId)
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Isolates notification exceptions from corrupting unlock verification outcomes
        }
    }

    /**
     * Clears notification attempts and active alerts during account logout or switch.
     */
    fun invalidateAccount(accountUid: String) {
        val cleanUid = accountUid.trim()
        if (cleanUid.isBlank()) return
        synchronized(lock) {
            notificationAdapter.cancelAccountNotifications(cleanUid)
            attemptStore.clearAccount(cleanUid)
        }
    }
}
