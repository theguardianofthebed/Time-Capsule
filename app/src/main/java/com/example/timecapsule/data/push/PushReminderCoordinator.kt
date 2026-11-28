package com.example.timecapsule.data.push

import com.example.timecapsule.data.session.SessionTracker
import com.example.timecapsule.data.session.StorageSession
import com.example.timecapsule.domain.model.PushReminderEvent
import java.time.Instant

/**
 * Transport-independent reminder gate, intentionally not wired to FCM or the application yet.
 * A reminder never calls an unlock API or borrows #19's confirmed-unlock display semantics.
 * Production binding updates must use the same session lock as account transitions.
 */
class PushReminderCoordinator(
    private val sessions: SessionTracker,
    private val currentBinding: () -> PushRegistrationBinding?,
    private val receipts: PushReceiptStore,
    private val renderer: PushReminderRenderer,
    private val now: () -> Instant = Instant::now
) {
    /**
     * Rejects unsafe payloads before side effects and claims before posting.
     * The session lock serializes claims/posts with #20 generation changes. Rechecks also reject
     * reentrant callbacks which change the session, binding or validity window inside an adapter.
     * An ambiguous display failure keeps its claim; it cannot certify exactly-once delivery.
     */
    fun handle(data: Map<String, String>): PushHandlingResult {
        val event = PushReminderEvent.parse(data) ?: return PushHandlingResult.INVALID_PAYLOAD
        return synchronized(StorageSession.lock) {
            try {
                val binding = currentBinding() ?: return@synchronized PushHandlingResult.NO_ACTIVE_BINDING
                val captured = sessions.currentSession()
                if (!captured.isAuthenticated || captured.sessionGeneration <= 0 ||
                    binding.session != captured) return@synchronized PushHandlingResult.NO_ACTIVE_BINDING
                if (captured.uid != event.accountUid) return@synchronized PushHandlingResult.WRONG_ACCOUNT
                if (binding.bindingId != event.bindingId) return@synchronized PushHandlingResult.STALE_BINDING
                validity(event)?.let { return@synchronized it }
                if (!renderer.isPermitted()) return@synchronized PushHandlingResult.PERMISSION_BLOCKED
                if (!isCurrent(binding)) return@synchronized PushHandlingResult.SESSION_CHANGED
                val key = PushReceiptKey(event.accountUid, event.bindingId, event.eventId)
                val fingerprint = event.fingerprint()
                when (receipts.claim(key, fingerprint)) {
                    PushClaimResult.DUPLICATE -> return@synchronized PushHandlingResult.DUPLICATE
                    PushClaimResult.CONFLICT -> return@synchronized PushHandlingResult.CONFLICT
                    PushClaimResult.CLAIMED -> Unit
                }
                if (!isCurrent(binding)) return@synchronized PushHandlingResult.SESSION_CHANGED
                validity(event)?.let {
                    receipts.releaseUnattempted(key, fingerprint)
                    return@synchronized it
                }
                // Do not release on a thrown/ambiguous post: an OS side effect may have happened.
                val result = try { renderer.postReminder(event) } catch (_: Exception) {
                    return@synchronized PushHandlingResult.POST_FAILED
                }
                when (result) {
                    PushPostResult.ATTEMPTED -> PushHandlingResult.POST_ATTEMPTED
                    PushPostResult.ATTEMPT_FAILED -> PushHandlingResult.POST_FAILED
                    PushPostResult.NOT_ATTEMPTED -> {
                        receipts.releaseUnattempted(key, fingerprint)
                        PushHandlingResult.POST_UNATTEMPTED
                    }
                }
            } catch (_: Exception) {
                // Store or permission failures never become a successful post or access decision.
                PushHandlingResult.UNAVAILABLE
            }
        }
    }

    /** Includes both the exact generation and opaque registration binding in the final check. */
    private fun isCurrent(binding: PushRegistrationBinding): Boolean =
        sessions.isSessionValid(binding.session) && sessions.currentSession() == binding.session &&
            currentBinding() == binding

    /** Device time is an expiry/display filter only; it never authorizes server-side unlock. */
    private fun validity(event: PushReminderEvent): PushHandlingResult? {
        val instant = now()
        return when {
            instant < event.notBefore -> PushHandlingResult.TOO_EARLY
            instant >= event.expiresAt -> PushHandlingResult.EXPIRED
            else -> null
        }
    }
}
