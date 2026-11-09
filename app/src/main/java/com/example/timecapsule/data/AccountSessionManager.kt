package com.example.timecapsule.data

import com.example.timecapsule.data.adapter.AuthAdapter
import com.example.timecapsule.data.adapter.FirestoreAdapter
import com.example.timecapsule.data.session.SessionTracker
import com.example.timecapsule.domain.model.RecipientConfirmation
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.result.OperationResult

/**
 * Orchestrates authentication, SessionTracker updates, profile creation/repair, and recipient confirmation boundaries.
 * Enforces single session generation updates and authenticated profile repair boundaries.
 */
class AccountSessionManager(
    private val authAdapter: AuthAdapter,
    private val firestoreAdapter: FirestoreAdapter,
    private val sessionTracker: SessionTracker
) {

    /**
     * Restores active user session from [authAdapter] on application restart.
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
        val result = authAdapter.signIn(email, password)
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
        val trimmedName = displayName.trim()
        if (trimmedName.isBlank()) {
            return OperationResult.Invalid("Display name must not be blank.")
        }

        // If user is already authenticated in Auth, attempt profile repair directly without duplicate Auth creation
        val activeUid = authAdapter.activeUid()
        if (!activeUid.isNullOrBlank()) {
            val repairRes = repairProfile(trimmedName)
            return if (repairRes is OperationResult.Success) {
                OperationResult.Success(activeUid)
            } else {
                OperationResult.Unavailable("Account exists, but recipient profile setup failed. Please perform Profile Repair.")
            }
        }

        val authResult = authAdapter.signUp(email, password, trimmedName)
        if (authResult is OperationResult.Success) {
            val uid = authResult.data
            sessionTracker.startSession(uid)
            val profile = RecipientProfile(uid = uid, displayName = trimmedName)
            val profileResult = firestoreAdapter.saveRecipientProfile(profile)
            if (profileResult !is OperationResult.Success) {
                return OperationResult.Unavailable(
                    "Account registered, but recipient profile setup failed. Please click 'Update Profile' to complete setup."
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

        // 1. Update Auth display name
        val authUpdateRes = authAdapter.updateDisplayName(trimmedName)
        if (authUpdateRes !is OperationResult.Success) {
            return authUpdateRes
        }

        // 2. Repair Firestore recipientDirectory document for the authenticated UID
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

        return when (val res = firestoreAdapter.getRecipientProfile(cleanUid)) {
            is OperationResult.Success -> OperationResult.Success(RecipientConfirmation(profile = res.data, isConfirmed = false))
            is OperationResult.Invalid -> OperationResult.Invalid(res.message)
            is OperationResult.Denied -> OperationResult.Denied(res.message)
            is OperationResult.Unavailable -> OperationResult.Unavailable(res.message)
            is OperationResult.OutcomeUnknown -> OperationResult.Unavailable(res.message)
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
