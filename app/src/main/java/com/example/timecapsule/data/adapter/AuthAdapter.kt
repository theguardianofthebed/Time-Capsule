package com.example.timecapsule.data.adapter

import com.example.timecapsule.domain.result.OperationResult

/**
 * Replaceable platform adapter boundary for authentication, session status, and user management.
 */
interface AuthAdapter {
    /** Returns the active user ID or null if unauthenticated. */
    fun activeUid(): String?

    /** Returns the active user email or null if unauthenticated. */
    fun activeEmail(): String?

    /** Returns the active user display name or null if unauthenticated. */
    fun activeDisplayName(): String?

    /** Authenticates user session with [email] and [password]. Returns user UID on success. */
    suspend fun signIn(email: String, password: String): OperationResult<String>

    /** Creates a new user account with [email], [password], and optional [displayName]. Returns user UID. */
    suspend fun signUp(email: String, password: String, displayName: String): OperationResult<String>

    /** Updates active user's profile display name. */
    suspend fun updateDisplayName(displayName: String): OperationResult<Unit>

    /** Sign out current authenticated user session. */
    fun signOut()
}
