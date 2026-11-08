package com.example.timecapsule.data.session

import com.example.timecapsule.domain.session.SessionProvider
import com.example.timecapsule.domain.session.SessionSnapshot

/**
 * Interface for managing and validating active user session generations.
 */
interface SessionTracker : SessionProvider {

    /** Starts a new session for [uid], incrementing the session generation counter. */
    fun startSession(uid: String?)

    /** Clears active session state and increments generation counter to invalidate callbacks. */
    fun clearSession()

    /**
     * Validates whether [capturedSnapshot] still matches the currently active session.
     * Returns true only if UID and session generation remain identical.
     */
    fun isSessionValid(capturedSnapshot: SessionSnapshot): Boolean
}
