package com.example.timecapsule.data.adapter

/**
 * Replaceable platform adapter boundary for authentication and session status.
 */
interface AuthAdapter {
    /** Returns the active user ID or null if unauthenticated. */
    fun activeUid(): String?

    /** Sign out current authenticated user session. */
    fun signOut()
}
