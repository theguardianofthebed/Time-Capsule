package com.example.timecapsule.data.push

import com.example.timecapsule.domain.model.PushReminderEvent
import com.example.timecapsule.domain.session.SessionSnapshot

/** A future FCM registration is bound to one exact local login generation and opaque binding ID. */
data class PushRegistrationBinding(val session: SessionSnapshot, val bindingId: String)

/** Keys keep accounts and separate logins independent even when the server reuses an event ID. */
data class PushReceiptKey(val accountUid: String, val bindingId: String, val eventId: String)

/** Claim outcomes distinguish true duplicates from the same ID carrying different content. */
enum class PushClaimResult { CLAIMED, DUPLICATE, CONFLICT }

/**
 * Future runtime implementations must durably claim before posting and survive process recreation.
 * No runtime store is installed by this foundation. Methods run under the shared session lock.
 */
interface PushReceiptStore {
    /** Atomically records a fingerprint or compares the previously recorded fingerprint. */
    fun claim(key: PushReceiptKey, fingerprint: String): PushClaimResult

    /** Removes only the matching claim when the renderer proves no OS post was attempted. */
    fun releaseUnattempted(key: PushReceiptKey, fingerprint: String)
}

/** Whether an OS display side effect was attempted; it does not certify user-visible delivery. */
enum class PushPostResult { ATTEMPTED, NOT_ATTEMPTED, ATTEMPT_FAILED }

/**
 * Reminder display boundary, deliberately distinct from #19's confirmed-unlock notification API.
 * Implementations use a generic reminder, account-safe entry and #20-compatible cancellation.
 * Calls must stay short and must not wait for a UI-thread dispatch or perform network requests.
 */
interface PushReminderRenderer {
    /** Checks runtime permission, app settings and notification channel availability without posting. */
    fun isPermitted(): Boolean

    /** Posts no protected content and performs no unlock or protected-content read. */
    fun postReminder(event: PushReminderEvent): PushPostResult
}

/** Explicit receipt decisions; none of these values authorize capsule content access. */
enum class PushHandlingResult {
    INVALID_PAYLOAD, NO_ACTIVE_BINDING, WRONG_ACCOUNT, STALE_BINDING, TOO_EARLY, EXPIRED,
    PERMISSION_BLOCKED, DUPLICATE, CONFLICT, SESSION_CHANGED, POST_ATTEMPTED,
    POST_UNATTEMPTED, POST_FAILED, UNAVAILABLE
}
