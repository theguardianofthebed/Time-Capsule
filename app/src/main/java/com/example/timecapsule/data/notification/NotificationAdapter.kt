package com.example.timecapsule.data.notification

/**
 * Replaceable adapter boundary for system notification operations.
 * Isolates Android OS notification APIs from application domain logic.
 */
interface NotificationAdapter {

    /**
     * Checks whether local notifications are enabled at the application and OS system level.
     *
     * @return True if notifications are permitted and enabled.
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
     * @return True if system notification post succeeded.
     */
    fun postUnlockNotification(accountUid: String, capsuleId: String): Boolean

    /**
     * Cancels posted notifications associated with [accountUid].
     *
     * @param accountUid The account UID whose notifications should be cleared.
     */
    fun cancelAccountNotifications(accountUid: String)
}
