package com.example.timecapsule.data.notification

/**
 * Detailed outcome result of a local notification posting attempt.
 */
enum class NotificationPostResult {
    /** Posting skipped because runtime notification permission or app-wide notifications are disabled. */
    NOT_ATTEMPTED_PERMISSION_DENIED,

    /** Posting skipped because notification channel is blocked (IMPORTANCE_NONE). */
    NOT_ATTEMPTED_CHANNEL_BLOCKED,

    /** System notification was successfully posted to OS NotificationManager. */
    ATTEMPTED_SUCCESS,

    /** Posting was attempted but failed due to system exception. */
    ATTEMPTED_FAILED
}

/**
 * Replaceable adapter boundary for system notification operations.
 * Isolates Android OS notification APIs from application domain logic.
 */
interface NotificationAdapter {

    /**
     * Checks whether local notifications are enabled at application, channel, and OS system levels.
     *
     * @return True if notifications are permitted and channel is not blocked.
     */
    fun areNotificationsPermitted(): Boolean

    /**
     * Ensures the notification channel exists on supported Android OS versions (API 26+).
     */
    fun createNotificationChannel()

    /**
     * Posts a generic account-safe local notification for [accountUid] and [capsuleId].
     * Never displays private capsule content, sender details, or lock conditions in the alert payload.
     *
     * @param accountUid The authenticated recipient UID.
     * @param capsuleId The verified capsule ID.
     * @return [NotificationPostResult] distinguishing unattempted permission blocks from actual posting attempts.
     */
    fun postUnlockNotification(accountUid: String, capsuleId: String): NotificationPostResult

    /**
     * Cancels posted notifications associated with [accountUid].
     *
     * @param accountUid The account UID whose notifications should be cleared.
     */
    fun cancelAccountNotifications(accountUid: String)
}
