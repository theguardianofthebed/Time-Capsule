package com.example.timecapsule.data.push

import com.example.timecapsule.data.session.DefaultSessionTracker
import com.example.timecapsule.domain.model.PushReminderEvent
import java.io.IOException
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Controlled adapters test real gate decisions; no test claims Android or FCM delivery. */
class PushReminderCoordinatorTest {
    private val sessions = DefaultSessionTracker().apply { startSession("account-1") }
    private var binding: PushRegistrationBinding? =
        PushRegistrationBinding(sessions.currentSession(), "login-1")
    private var time = Instant.parse("2026-11-28T12:00:00Z")
    private val store = Receipts()
    private val renderer = Renderer()

    /** Supplies a minimal allow-listed routing event. */
    private fun payload() = mapOf("schemaVersion" to "1", "eventType" to "TIME_REMINDER",
        "eventId" to "event-1", "capsuleId" to "capsule-1", "accountUid" to "account-1",
        "bindingId" to "login-1", "notBefore" to "2026-11-28T00:00:00Z",
        "expiresAt" to "2026-11-29T00:00:00Z")

    /** Creates independent coordinators sharing one receipt store to exercise duplicate handling. */
    private fun coordinator() = PushReminderCoordinator(sessions, { binding }, store, renderer, { time })

    /** Test-only memory storage is not offered as a durable production implementation. */
    private class Receipts : PushReceiptStore {
        val claims = mutableMapOf<PushReceiptKey, String>()
        var onClaim: () -> Unit = {}
        var fail = false
        /** Compares real fingerprints and exposes a deterministic callback race for the test. */
        override fun claim(key: PushReceiptKey, fingerprint: String): PushClaimResult {
            if (fail) throw IOException("Fixture storage failure")
            val previous = claims.putIfAbsent(key, fingerprint)
            onClaim()
            return when (previous) {
                null -> PushClaimResult.CLAIMED
                fingerprint -> PushClaimResult.DUPLICATE
                else -> PushClaimResult.CONFLICT
            }
        }
        /** Deletes only the original matching claim, as required by the runtime contract. */
        override fun releaseUnattempted(key: PushReceiptKey, fingerprint: String) {
            if (claims[key] == fingerprint) claims.remove(key)
        }
    }

    /** Records display attempts and lets tests inject permission and callback races. */
    private class Renderer : PushReminderRenderer {
        var permitted = true
        var posts = 0
        var result = PushPostResult.ATTEMPTED
        var onPermission: () -> Unit = {}
        var onPost: () -> Unit = {}
        /** Allows a test to invalidate a session during the permission preflight. */
        override fun isPermitted(): Boolean {
            onPermission()
            return permitted
        }
        /** Records an attempted call while permitting OS-failure simulation. */
        override fun postReminder(event: PushReminderEvent): PushPostResult {
            posts++
            onPost()
            return result
        }
    }

    /** Valid routing and a live binding reach the generic display boundary once. */
    @Test fun postsEligibleReminder() {
        assertEquals(PushHandlingResult.POST_ATTEMPTED, coordinator().handle(payload()))
        assertEquals(1, renderer.posts)
    }

    /** Untrusted extensions cannot reach permission, claim or display adapters. */
    @Test fun rejectsMalformedPayloadBeforeSideEffects() {
        assertEquals(PushHandlingResult.INVALID_PAYLOAD, coordinator().handle(payload() + ("text" to "secret")))
        assertTrue(store.claims.isEmpty())
        assertEquals(0, renderer.posts)
    }

    /** Absence of a registration is not silently treated as opt-in. */
    @Test fun rejectsMissingBinding() {
        binding = null
        assertEquals(PushHandlingResult.NO_ACTIVE_BINDING, coordinator().handle(payload()))
    }

    /** A signed-out account cannot display a previously registered event. */
    @Test fun rejectsSignedOutSession() {
        sessions.clearSession()
        assertEquals(PushHandlingResult.NO_ACTIVE_BINDING, coordinator().handle(payload()))
        assertEquals(0, renderer.posts)
    }

    /** The event's account cannot select another user's device. */
    @Test fun rejectsWrongAccount() {
        assertEquals(PushHandlingResult.WRONG_ACCOUNT,
            coordinator().handle(payload() + ("accountUid" to "other")))
        assertTrue(store.claims.isEmpty())
    }

    /** A new login invalidates events addressed to an earlier registration binding. */
    @Test fun rejectsOldBinding() {
        assertEquals(PushHandlingResult.STALE_BINDING,
            coordinator().handle(payload() + ("bindingId" to "previous-login")))
    }

    /** Same UID authentication still requires a current generation-bound registration. */
    @Test fun rejectsSameUidNewGeneration() {
        sessions.startSession("account-1")
        assertEquals(PushHandlingResult.NO_ACTIVE_BINDING, coordinator().handle(payload()))
    }

    /** A reminder cannot be displayed before its specified display window. */
    @Test fun rejectsEarlyEvent() {
        time = Instant.parse("2026-11-27T23:59:59Z")
        assertEquals(PushHandlingResult.TOO_EARLY, coordinator().handle(payload()))
    }

    /** The beginning of the display window is inclusive. */
    @Test fun acceptsStartBoundary() {
        time = Instant.parse("2026-11-28T00:00:00Z")
        assertEquals(PushHandlingResult.POST_ATTEMPTED, coordinator().handle(payload()))
    }

    /** Expiry is exclusive and does not consume a receipt claim. */
    @Test fun rejectsExpiryBoundary() {
        time = Instant.parse("2026-11-29T00:00:00Z")
        assertEquals(PushHandlingResult.EXPIRED, coordinator().handle(payload()))
        assertTrue(store.claims.isEmpty())
    }

    /** Permission denial does not consume an attempt; granting later may still display. */
    @Test fun permissionRefusalCanBeRetried() {
        renderer.permitted = false
        assertEquals(PushHandlingResult.PERMISSION_BLOCKED, coordinator().handle(payload()))
        assertTrue(store.claims.isEmpty())
        renderer.permitted = true
        assertEquals(PushHandlingResult.POST_ATTEMPTED, coordinator().handle(payload()))
    }

    /** Recreated coordinators use the same logical claim, not process-local instance flags. */
    @Test fun duplicateAcrossCoordinatorInstancesIsSuppressed() {
        coordinator().handle(payload())
        assertEquals(PushHandlingResult.DUPLICATE, coordinator().handle(payload()))
        assertEquals(1, renderer.posts)
    }

    /** Reused event IDs with changed capsule metadata do not become harmless duplicates. */
    @Test fun conflictingEventIdIsRejected() {
        coordinator().handle(payload())
        assertEquals(PushHandlingResult.CONFLICT,
            coordinator().handle(payload() + ("capsuleId" to "different")))
        assertEquals(1, renderer.posts)
    }

    /** Changing session during permission lookup prevents even the disk claim. */
    @Test fun permissionCallbackCannotOutliveSession() {
        renderer.onPermission = { sessions.clearSession() }
        assertEquals(PushHandlingResult.SESSION_CHANGED, coordinator().handle(payload()))
        assertTrue(store.claims.isEmpty())
        assertEquals(0, renderer.posts)
    }

    /** Changing session inside a store callback is checked again before the OS side effect. */
    @Test fun claimCallbackCannotPostAfterLogout() {
        store.onClaim = { sessions.clearSession() }
        assertEquals(PushHandlingResult.SESSION_CHANGED, coordinator().handle(payload()))
        assertEquals(0, renderer.posts)
    }

    /** Registration refresh during the claim cannot display the former binding's event. */
    @Test fun bindingChangeBeforePostIsRejected() {
        store.onClaim = { binding = binding!!.copy(bindingId = "new-login") }
        assertEquals(PushHandlingResult.SESSION_CHANGED, coordinator().handle(payload()))
        assertEquals(0, renderer.posts)
    }

    /** The final validity check rejects an event which expires during a slow store operation. */
    @Test fun expiryDuringClaimDoesNotPost() {
        store.onClaim = { time = Instant.parse("2026-11-29T00:00:00Z") }
        assertEquals(PushHandlingResult.EXPIRED, coordinator().handle(payload()))
        assertTrue(store.claims.isEmpty())
        assertEquals(0, renderer.posts)
    }

    /** A renderer-confirmed no-attempt result releases only its own claim for later permission retry. */
    @Test fun unattemptedPostCanRetry() {
        renderer.result = PushPostResult.NOT_ATTEMPTED
        assertEquals(PushHandlingResult.POST_UNATTEMPTED, coordinator().handle(payload()))
        assertTrue(store.claims.isEmpty())
        renderer.result = PushPostResult.ATTEMPTED
        assertEquals(PushHandlingResult.POST_ATTEMPTED, coordinator().handle(payload()))
    }

    /** An uncertain platform exception must not release a claim and accidentally display twice. */
    @Test fun thrownPostRetainsClaim() {
        renderer.onPost = { throw IOException("Unknown OS result") }
        assertEquals(PushHandlingResult.POST_FAILED, coordinator().handle(payload()))
        assertEquals(PushHandlingResult.DUPLICATE, coordinator().handle(payload()))
        assertEquals(1, renderer.posts)
    }

    /** A reported attempted failure has the same conservative duplicate boundary as an exception. */
    @Test fun attemptedFailureRetainsClaim() {
        renderer.result = PushPostResult.ATTEMPT_FAILED
        assertEquals(PushHandlingResult.POST_FAILED, coordinator().handle(payload()))
        assertEquals(PushHandlingResult.DUPLICATE, coordinator().handle(payload()))
    }

    /** Storage failure cannot be converted into an apparent successful notification. */
    @Test fun storeFailureFailsClosed() {
        store.fail = true
        assertEquals(PushHandlingResult.UNAVAILABLE, coordinator().handle(payload()))
        assertEquals(0, renderer.posts)
    }

    /** The claim exists when display is called, even if the display operation immediately throws. */
    @Test fun claimsBeforePosting() {
        renderer.onPost = { assertEquals(1, store.claims.size) }
        assertEquals(PushHandlingResult.POST_ATTEMPTED, coordinator().handle(payload()))
    }

    /** Concurrent same-event receipts serialize on the shared session boundary. */
    @Test fun concurrentReceiptsPostOnce() {
        val pool = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        try {
            val results = (1..2).map {
                pool.submit<PushHandlingResult> { start.await(); coordinator().handle(payload()) }
            }
            start.countDown()
            val outcomes = results.map { it.get(5, TimeUnit.SECONDS) }.toSet()
            assertEquals(setOf(PushHandlingResult.POST_ATTEMPTED, PushHandlingResult.DUPLICATE), outcomes)
            assertEquals(1, renderer.posts)
        } finally { pool.shutdownNow() }
    }
}
