package com.example.timecapsule.data

import com.example.timecapsule.data.adapter.AuthAdapter
import com.example.timecapsule.data.adapter.FirestoreAdapter
import com.example.timecapsule.data.session.SessionTracker
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.result.OperationResult

/**
 * Orchestrates authentication, SessionTracker updates, and Profile Repair operations.
 * Enforces single session generation updates and authenticated profile repair boundaries.
 */
class AccountSessionManager(
    private val authAdapter: AuthAdapter,
    private val firestoreAdapter: FirestoreAdapter,
    private val sessionTracker: SessionTracker
) {

    /** Performs user sign in and updates SessionTracker exactly once on success. */
    suspend fun signIn(email: String, password: String): OperationResult<String> {
        val result = authAdapter.signIn(email, password)
        if (result is OperationResult.Success) {
            sessionTracker.startSession(result.data)
        }
        return result
    }

    /** Performs user sign up, starts session exactly once, and initializes `recipientDirectory/{uid}` profile. */
    suspend fun signUpAndCreateProfile(
        email: String,
        password: String,
        displayName: String
    ): OperationResult<String> {
        val authResult = authAdapter.signUp(email, password, displayName)
        if (authResult is OperationResult.Success) {
            val uid = authResult.data
            sessionTracker.startSession(uid)
            val profile = RecipientProfile(uid = uid, displayName = displayName.trim())
            val profileResult = firestoreAdapter.saveRecipientProfile(profile)
            if (profileResult !is OperationResult.Success) {
                // Return profile creation failure warning so UI can initiate Profile Repair if needed
                return OperationResult.OutcomeUnknown(
                    capsuleId = uid,
                    message = "Account created but recipient profile setup failed. Please perform Profile Repair."
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

    /** Signs out the current user and clears session tracker exactly once. */
    fun signOut() {
        authAdapter.signOut()
        sessionTracker.clearSession()
    }
}
