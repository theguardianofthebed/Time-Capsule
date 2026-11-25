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
 * 1. Durable Claim Before Post: Claims attempt in persistent store FIRST before invoking the Android notification side effect.
 * 2. Confirmed First Unlock (Allowed): Claims attempt, then posts local alert. Restores pending state if definitively not attempted.
 * 3. Confirmed First Unlock (Denied/Blocked): Preserves candidate in [AttemptState.PENDING_PERMISSION].
 * 4. Already Unlocked (Pending Permission & Allowed): Claims attempt and posts once for pending candidate.
 * 5. Already Unlocked (Absent): Does NOT post historical alerts for already unlocked capsules.
 * 6. Session Protection: Revalidates immutable [SessionSnapshot] before attempt claim and posting.
 * 7. Exception Isolation: Notification or storage failures are caught and isolated so unlock verification
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

                val shouldAttempt = when (outcome.confirmationKind) {
                    UnlockConfirmationKind.FIRST_UNLOCK_CONFIRMED -> currentState != AttemptState.ATTEMPTED
                    UnlockConfirmationKind.ALREADY_UNLOCKED,
                    UnlockConfirmationKind.RECONCILED_AVAILABLE -> currentState == AttemptState.PENDING_PERMISSION
                }

                if (!shouldAttempt) return

                if (!notificationAdapter.areNotificationsPermitted()) {
                    if (outcome.confirmationKind == UnlockConfirmationKind.FIRST_UNLOCK_CONFIRMED) {
                        attemptStore.recordPendingPermission(accountUid, cleanCapsuleId)
                    }
                    return
                }

                // 1. DURABLE CLAIM BEFORE POST
                if (!attemptStore.atomicallyClaimAttempt(accountUid, cleanCapsuleId)) {
                    return
                }

                // Revalidate session snapshot
                if (!sessionTracker.isSessionValid(targetSession) || sessionTracker.currentSession() != targetSession) {
                    return
                }

                // 2. ANDROID NOTIFICATION SIDE EFFECT
                val postResult = notificationAdapter.postUnlockNotification(accountUid, cleanCapsuleId)
                when (postResult) {
                    NotificationPostResult.NOT_ATTEMPTED_PERMISSION_DENIED,
                    NotificationPostResult.NOT_ATTEMPTED_CHANNEL_BLOCKED -> {
                        // Restore pending permission marker if posting was definitively not attempted
                        attemptStore.restorePendingPermissionIfClaimed(accountUid, cleanCapsuleId)
                    }
                    NotificationPostResult.ATTEMPTED_SUCCESS,
                    NotificationPostResult.ATTEMPTED_FAILED -> {
                        // Keep durable ATTEMPTED claim
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
