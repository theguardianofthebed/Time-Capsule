package com.example.timecapsule.data.session

import com.example.timecapsule.data.CapsuleRepository
import com.example.timecapsule.domain.model.Availability
import com.example.timecapsule.domain.model.CapsuleDraft
import com.example.timecapsule.domain.model.CapsuleListsSnapshot
import com.example.timecapsule.domain.model.ProtectedCapsuleContent
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.model.SendStatus
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic delayed repository test double to verify operation-boundary session generation tracking.
 * Simulates asynchronous network or database delay while capturing the entry session snapshot.
 */
private class DelayedFakeRepository(
    private val sessionTracker: DefaultSessionTracker,
    private val delayMs: Long = 50L
) : CapsuleRepository {

    override fun session(): SessionSnapshot = sessionTracker.currentSession()

    override suspend fun getRecipient(uid: String): OperationResult<RecipientProfile> {
        val captured = sessionTracker.currentSession()
        delay(delayMs)
        // Operation boundary check: reject delayed result if session generation changed during execution.
        if (!sessionTracker.isSessionValid(captured)) {
            return OperationResult.Denied("Stale session snapshot rejected")
        }
        if (!captured.isAuthenticated) return OperationResult.Denied("Unauthenticated session")
        return OperationResult.Success(RecipientProfile(uid, "User $uid"))
    }

    override suspend fun publish(frozenOperation: CapsuleDraft): OperationResult<String> {
        val captured = sessionTracker.currentSession()
        delay(delayMs)
        if (!sessionTracker.isSessionValid(captured)) {
            return OperationResult.Denied("Stale session snapshot rejected")
        }
        if (!captured.isAuthenticated) return OperationResult.Denied("Unauthenticated session")
        return OperationResult.Success(frozenOperation.capsuleId)
    }

    override suspend fun reconcile(capsuleId: String): OperationResult<SendStatus> {
        val captured = sessionTracker.currentSession()
        delay(delayMs)
        if (!sessionTracker.isSessionValid(captured)) {
            return OperationResult.Denied("Stale session snapshot rejected")
        }
        if (!captured.isAuthenticated) return OperationResult.Denied("Unauthenticated session")
        return OperationResult.Success(SendStatus.SENT)
    }

    override suspend fun lists(uid: String): OperationResult<CapsuleListsSnapshot> {
        val captured = sessionTracker.currentSession()
        delay(delayMs)
        if (!sessionTracker.isSessionValid(captured)) {
            return OperationResult.Denied("Stale session snapshot rejected")
        }
        if (!captured.isAuthenticated) return OperationResult.Denied("Unauthenticated session")
        if (captured.uid != uid) return OperationResult.Denied("Cross-account list access denied")
        return OperationResult.Success(CapsuleListsSnapshot(emptyList(), emptyList()))
    }

    override suspend fun verifyUnlock(capsuleId: String): OperationResult<Availability> {
        val captured = sessionTracker.currentSession()
        delay(delayMs)
        if (!sessionTracker.isSessionValid(captured)) {
            return OperationResult.Denied("Stale session snapshot rejected")
        }
        if (!captured.isAuthenticated) return OperationResult.Denied("Unauthenticated session")
        return OperationResult.Success(Availability.LOCKED)
    }

    override suspend fun readContent(capsuleId: String): OperationResult<ProtectedCapsuleContent> {
        val captured = sessionTracker.currentSession()
        delay(delayMs)
        if (!sessionTracker.isSessionValid(captured)) {
            return OperationResult.Denied("Stale session snapshot rejected")
        }
        if (!captured.isAuthenticated) return OperationResult.Denied("Unauthenticated session")
        return OperationResult.Denied("Unlock authorization required before reading content")
    }
}

/**
 * Unit tests for [DefaultSessionTracker] verifying session generation tracking
 * and stale callback rejection across account switches, sign-out events, and async delays.
 */
class SessionProtectionTest {

    private val tracker = DefaultSessionTracker()
    private val delayedRepo = DelayedFakeRepository(tracker, delayMs = 50L)

    @Test
    fun normalSessionIsValid() {
        tracker.startSession("user-a")
        val captured = tracker.currentSession()

        assertTrue(tracker.isSessionValid(captured))
    }

    @Test
    fun sessionInvalidatedOnLogout() {
        tracker.startSession("user-a")
        val captured = tracker.currentSession()

        tracker.clearSession()

        assertFalse(tracker.isSessionValid(captured))
        assertFalse(tracker.currentSession().isAuthenticated)
    }

    @Test
    fun sessionInvalidatedOnAccountSwitch() {
        tracker.startSession("user-a")
        val capturedA = tracker.currentSession()

        tracker.startSession("user-b")

        assertFalse(tracker.isSessionValid(capturedA))
        assertTrue(tracker.isSessionValid(tracker.currentSession()))
    }

    @Test
    fun newGenerationForSameUidInvalidatesPreviousSnapshot() {
        tracker.startSession("user-a")
        val snapshot1 = tracker.currentSession()

        // Re-authenticate user-a in a new session generation
        tracker.startSession("user-a")

        assertFalse(tracker.isSessionValid(snapshot1))
        assertTrue(tracker.isSessionValid(tracker.currentSession()))
    }

    @Test
    fun delayedOperationRejectsResultOnSignOut() = runBlocking {
        tracker.startSession("user-a")

        // Start delayed repository operation in coroutine
        val job = async { delayedRepo.getRecipient("user-b") }

        // Simulate user sign-out while async operation is in-flight
        delay(10L)
        tracker.clearSession()

        val result = job.await()
        assertTrue("Delayed result must be Denied on sign-out", result is OperationResult.Denied)
        assertEquals("Stale session snapshot rejected", (result as OperationResult.Denied).message)
    }

    @Test
    fun delayedOperationRejectsResultOnAccountSwitch() = runBlocking {
        tracker.startSession("user-a")

        val job = async { delayedRepo.lists("user-a") }

        // Simulate user switching from user-a to user-b while async operation is in-flight
        delay(10L)
        tracker.startSession("user-b")

        val result = job.await()
        assertTrue("Delayed result must be Denied on account switch", result is OperationResult.Denied)
        assertEquals("Stale session snapshot rejected", (result as OperationResult.Denied).message)
    }

    @Test
    fun delayedOperationRejectsResultOnNewSessionGenerationForSameUid() = runBlocking {
        tracker.startSession("user-a")

        val job = async { delayedRepo.getRecipient("user-b") }

        // Simulate re-authentication of user-a generating a new session counter
        delay(10L)
        tracker.startSession("user-a")

        val result = job.await()
        assertTrue("Delayed result must be Denied on new generation", result is OperationResult.Denied)
        assertEquals("Stale session snapshot rejected", (result as OperationResult.Denied).message)
    }
}
