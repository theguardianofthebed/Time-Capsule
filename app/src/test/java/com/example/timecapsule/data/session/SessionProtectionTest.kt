package com.example.timecapsule.data.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [DefaultSessionTracker] verifying session generation tracking
 * and stale callback rejection across account switches and sign-out events.
 */
class SessionProtectionTest {

    private val tracker = DefaultSessionTracker()

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
}
