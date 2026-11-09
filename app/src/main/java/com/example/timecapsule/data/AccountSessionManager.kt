package com.example.timecapsule.data

import com.example.timecapsule.data.adapter.AuthAdapter
import com.example.timecapsule.data.adapter.FirestoreAdapter
import com.example.timecapsule.data.session.SessionGuard
import com.example.timecapsule.data.session.SessionTracker
import com.example.timecapsule.domain.model.RecipientConfirmation
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.result.OperationResult

/**
 * Orchestrates authentication, SessionTracker updates, profile creation/repair, and recipient confirmation boundaries.
 * Enforces single session generation updates, stale callback invalidation, and authenticated profile repair boundaries.
 */
class AccountSessionManager(
    private val authAdapter: AuthAdapter,
    private val firestoreAdapter: FirestoreAdapter,
    private val sessionTracker: SessionTracker,
    private val sessionGuard: SessionGuard = SessionGuard(sessionTracker)
) {

    /**
     * Restores active user session from [authAdapter] on application restart or container recreation.
     * Updates [sessionTracker] with active UID if authenticated, or clears session if unauthenticated.
     */
    fun restoreSessionIfAuthenticated(): String? {
        val activeUid = authAdapter.activeUid()
        if (!activeUid.isNullOrBlank()) {
            sessionTracker.startSession(activeUid)
            return activeUid
        } else {
            sessionTracker.clearSession()
            return null
        }
    }

    /** Performs user sign in and updates SessionTracker exactly once on success. */
    suspend fun signIn(email: String, password: String): OperationResult<String> {
        val capturedSession = sessionTracker.currentSession()
        val result = authAdapter.signIn(email, password)

        val currentSession = sessionTracker.currentSession()
        if (currentSession.sessionGeneration != capturedSession.sessionGeneration) {
            if (authAdapter.activeUid() != null && currentSession.uid != authAdapter.activeUid()) {
                authAdapter.signOut()
            }
            return OperationResult.Denied("Stale session invalidation: session changed during sign-in")
        }

        if (result is OperationResult.Success) {
            sessionTracker.startSession(result.data)
        }
        return result
    }

    /** Performs user sign up, starts session, and creates `recipientDirectory/{uid}` profile. */
    suspend fun signUpAndCreateProfile(
        email: String,
        password: String,
        displayName: String
    ): OperationResult<String> {
        val trimmedEmail = email.trim()
        val trimmedName = displayName.trim()
        if (trimmedName.isBlank()) {
            return OperationResult.Invalid("Display name must not be blank.")
        }
        if (trimmedEmail.isBlank() || password.isBlank()) {
            return OperationResult.Invalid("Email and password must not be blank.")
        }
        if (password.length < 6) {
            return OperationResult.Invalid("Password must be at least 6 characters.")
        }

        val capturedSession = sessionTracker.currentSession()

        // If user is already authenticated in Auth, attempt profile repair directly without duplicate Auth creation
        val activeUid = authAdapter.activeUid()
        if (!activeUid.isNullOrBlank()) {
            val repairRes = repairProfileInternal(activeUid, trimmedName)
            return if (repairRes is OperationResult.Success) {
                OperationResult.Success(activeUid)
            } else {
                OperationResult.Unavailable("Account exists, but recipient profile setup failed. Please update profile to complete setup.")
            }
        }

        val authResult = authAdapter.signUp(trimmedEmail, password, trimmedName)

        val currentSession = sessionTracker.currentSession()
        if (currentSession.sessionGeneration != capturedSession.sessionGeneration) {
            if (authAdapter.activeUid() != null && currentSession.uid != authAdapter.activeUid()) {
                authAdapter.signOut()
            }
            return OperationResult.Denied("Stale session invalidation: session changed during sign-up")
        }

        if (authResult is OperationResult.Success) {
            val uid = authResult.data
            sessionTracker.startSession(uid)

            val profile = RecipientProfile(uid = uid, displayName = trimmedName)
            val profileResult = firestoreAdapter.saveRecipientProfile(profile)
            if (profileResult !is OperationResult.Success) {
                return OperationResult.Unavailable(
                    "Account registered, but recipient profile setup failed. Please click 'Register / Repair' to complete setup."
                )
            }
        }
        return authResult
    }

    /**
     * Performs Profile Repair for an authenticated user session.
     * Requires an active, authenticated session and updates the user's `recipientDirectory/{uid}` profile.
     */
    suspend fun repairProfile(displayName: String): OperationResult<Unit> {
        val session = sessionTracker.currentSession()
        val activeUid = authAdapter.activeUid()

        if (!session.isAuthenticated || activeUid.isNullOrBlank() || session.uid != activeUid) {
            return OperationResult.Denied("Profile repair requires an active, authenticated user session.")
        }

        val trimmedName = displayName.trim()
        if (trimmedName.isBlank()) {
            return OperationResult.Invalid("Display name must not be blank for profile repair.")
        }

        return sessionGuard.runGuarded(session) {
            repairProfileInternal(activeUid, trimmedName)
        }
    }

    private suspend fun repairProfileInternal(activeUid: String, trimmedName: String): OperationResult<Unit> {
        val authUpdateRes = authAdapter.updateDisplayName(trimmedName)
        if (authUpdateRes !is OperationResult.Success) {
            return authUpdateRes
        }
        val profile = RecipientProfile(uid = activeUid, displayName = trimmedName)
        return firestoreAdapter.saveRecipientProfile(profile)
    }

    /**
     * Exact recipient profile lookup by [uid] with whitespace trimmed.
     * Returns [RecipientConfirmation] initialized with isConfirmed = false.
     */
    suspend fun lookupRecipient(uid: String): OperationResult<RecipientConfirmation> {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) {
            return OperationResult.Invalid("Recipient UID must not be blank.")
        }

        val capturedSession = sessionTracker.currentSession()
        return sessionGuard.runGuarded(capturedSession) {
            when (val res = firestoreAdapter.getRecipientProfile(cleanUid)) {
                is OperationResult.Success -> OperationResult.Success(RecipientConfirmation(profile = res.data, isConfirmed = false))
                is OperationResult.Invalid -> OperationResult.Invalid(res.message)
                is OperationResult.Denied -> OperationResult.Denied(res.message)
                is OperationResult.Unavailable -> OperationResult.Unavailable(res.message)
                is OperationResult.OutcomeUnknown -> OperationResult.Unavailable(res.message)
            }
        }
    }

    /**
     * Explicitly confirms or invalidates recipient selection [confirmation].
     */
    fun confirmRecipient(confirmation: RecipientConfirmation, confirm: Boolean): RecipientConfirmation {
        return confirmation.copy(isConfirmed = confirm)
    }

    /** Signs out the current user and clears session tracker exactly once. */
    fun signOut() {
        authAdapter.signOut()
        sessionTracker.clearSession()
    }
}
