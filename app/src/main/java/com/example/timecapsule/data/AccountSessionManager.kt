package com.example.timecapsule.data

import com.example.timecapsule.data.adapter.AuthAdapter
import com.example.timecapsule.data.adapter.FirestoreAdapter
import com.example.timecapsule.data.session.SessionGuard
import com.example.timecapsule.data.session.SessionTracker
import com.example.timecapsule.domain.model.RecipientConfirmation
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Orchestrates authentication, SessionTracker updates, profile creation/repair, and recipient confirmation boundaries.
 * Enforces single session generation updates, stale callback invalidation, and authenticated profile repair boundaries.
 */
class AccountSessionManager(
    private val authAdapter: AuthAdapter,
    private val firestoreAdapter: FirestoreAdapter,
    val sessionTracker: SessionTracker,
    private val sessionGuard: SessionGuard = SessionGuard(sessionTracker),
    private val onAccountCleared: (suspend (String) -> OperationResult<Unit>)? = null,
    private val isCleanupPending: () -> Boolean = { false },
    private val markCleanupPending: (suspend (String) -> Unit)? = null,
    private val getPendingCleanupUid: () -> String = { "" },
    private val storageDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val authMutex = Mutex()
    private val authentication = Mutex()

    /** Identities whose durable markers must outlive the asynchronous SDK call. */
    private data class PendingAuthentication(val origin: SessionSnapshot, val former: List<String>)

    /** Exposes immutable identity/generation evidence for rejecting stale foreground UI callbacks. */
    fun currentSession(): SessionSnapshot = sessionTracker.currentSession()

    /** Validates whether [session] matches the current active session in SessionTracker. */
    fun isSessionValid(session: SessionSnapshot): Boolean = sessionTracker.isSessionValid(session)

    /**
     * Restores active user session from [authAdapter] on application restart or container recreation.
     * Changes generation only on an identity transition; foreground checks preserve in-flight work.
     * Enforces session invalidation and account cleanup BEFORE activating a new session.
     */
    suspend fun restoreSessionIfAuthenticated(): String? = authMutex.withLock {
        if (authentication.isLocked) return@withLock currentSession().uid
        restoreLocked()
    }

    /** Reconciles the SDK identity only after former accounts have been durably retired. */
    private suspend fun restoreLocked(): String? = withContext(storageDispatcher) {
        val active = authAdapter.activeUid()
        val former = currentSession().uid
        if (active == former && !isCleanupPending()) return@withContext active
        if (former != null || isCleanupPending()) {
            if (retire(former?.takeUnless { it == active }, signOutSdk = false) !is OperationResult.Success) return@withContext null
        }
        if (active != null && authAdapter.activeUid() == active) {
            sessionTracker.startSession(active)
            return@withContext active
        }
        null
    }

    /** Captures former accounts before the SDK can change its persistent user. */
    private suspend fun prepareAuthentication(): PendingAuthentication? = withContext(storageDispatcher) {
        if (isCleanupPending() && retire(currentSession().uid, signOutSdk = false) !is OperationResult.Success) {
            return@withContext null
        }
        val former = listOfNotNull(authAdapter.activeUid(), currentSession().uid).distinct()
        if (former.isNotEmpty()) {
            if (!recordObligations(former)) return@withContext null
            sessionTracker.clearSession()
            if (!signOutConfirmed()) return@withContext null
        }
        PendingAuthentication(currentSession(), former)
    }

    /** Runs the SDK request without holding the transition lock, so logout can invalidate a late result. */
    private suspend fun authenticate(request: suspend () -> OperationResult<String>): OperationResult<String> {
        val attempt = authMutex.withLock { prepareAuthentication() } ?: return cleanupFailure()
        val caller = currentCoroutineContext()[Job]
        // Firebase may complete after the screen is cancelled. Always reconcile that SDK result.
        return withContext(NonCancellable) {
            val result = request()
            authMutex.withLock {
                staleResult(attempt.origin)?.let { return@withLock it }
                if (caller?.isCancelled == true) {
                    val cleared = retire(currentSession().uid, signOutSdk = true)
                    if (cleared !is OperationResult.Success) return@withLock cleanupFailure()
                    return@withLock OperationResult.Denied("Authentication was cancelled.")
                }
                val cleaned = withContext(storageDispatcher) {
                    attempt.former.all { uid ->
                        val cleanup = onAccountCleared?.invoke(uid)
                        cleanup == null || cleanup is OperationResult.Success
                    } && !isCleanupPending()
                }
                if (!cleaned) {
                    // Preserve the failed purge and prevent restoration of the unactivated SDK login.
                    val sdkUid = authAdapter.activeUid()
                    if (sdkUid != null && recordObligations(listOf(sdkUid))) signOutConfirmed()
                    return@withLock cleanupFailure()
                }
                if (result is OperationResult.Success) {
                    if (authAdapter.activeUid() != result.data) return@withLock OperationResult.Denied("Account changed.")
                    sessionTracker.startSession(result.data)
                }
                result
            }
        }
    }

    /** Authenticates one account and activates it after the former account cleanup completes. */
    suspend fun signIn(email: String, password: String): OperationResult<String> = authentication.withLock {
        authenticate { authAdapter.signIn(email, password) }
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

        return authentication.withLock {
            authMutex.withLock {
                if (authAdapter.activeUid() != null) {
                    val activeUid = restoreLocked() ?: return cleanupFailure()
                    val repair = repairProfileInternal(activeUid, trimmedName)
                    return if (repair is OperationResult.Success) OperationResult.Success(activeUid)
                    else OperationResult.Unavailable("Recipient profile setup failed.")
                }
            }
            val result = authenticate { authAdapter.signUp(trimmedEmail, password, trimmedName) }
            if (result is OperationResult.Success) {
                val session = currentSession()
                if (session.uid != result.data) return OperationResult.Denied("Account changed.")
                val profile = RecipientProfile(uid = result.data, displayName = trimmedName)
                val saved = sessionGuard.runGuarded(session) { firestoreAdapter.saveRecipientProfile(profile) }
                if (saved !is OperationResult.Success) {
                    return OperationResult.Unavailable("Account registered, but recipient profile setup failed. Please click 'Register / Repair' to complete setup.")
                }
            }
            result
        }
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

    /**
     * Verifies whether [uid] has a valid, existing `recipientDirectory/{uid}` profile.
     * Distinguishes a genuinely missing profile ([OperationResult.Invalid]) from network ([OperationResult.Unavailable])
     * or permission ([OperationResult.Denied]) failures.
     */
    suspend fun checkProfile(uid: String): OperationResult<RecipientProfile> {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) {
            return OperationResult.Invalid("UID must not be blank for profile check.")
        }
        val session = sessionTracker.currentSession()
        if (!session.isAuthenticated || session.uid != cleanUid) {
            return OperationResult.Denied("Profile check requires an active authenticated session for $cleanUid.")
        }
        return sessionGuard.runGuarded(session) {
            firestoreAdapter.getRecipientProfile(cleanUid)
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

    /**
     * Signs out current user following strict 4-step account transition lifecycle:
     * 1. Capture former account identity and persist cleanup obligation FIRST.
     * 2. Invalidate former session generation to block protected access immediately.
     * 3. Execute store cleanups and Auth sign-out.
     * 4. Confirm successful cleanup before removing pending obligation.
     */
    suspend fun signOut(): OperationResult<Unit> = authMutex.withLock {
        retire(currentSession().uid ?: authAdapter.activeUid(), signOutSdk = true)
    }

    /** Records every former identity before invalidation. The SDK sign-out precedes marker removal. */
    private suspend fun retire(former: String?, signOutSdk: Boolean): OperationResult<Unit> =
        withContext(storageDispatcher) {
            try {
                val sdkUid = authAdapter.activeUid().takeIf { signOutSdk }
                val accounts = listOfNotNull(former, sdkUid).distinct()
                if (!recordObligations(accounts)) return@withContext cleanupFailure()
                sessionTracker.clearSession()
                if (signOutSdk && !signOutConfirmed()) return@withContext cleanupFailure()
                for (uid in accounts) {
                    if (authAdapter.activeUid() == uid && !signOutConfirmed()) return@withContext cleanupFailure()
                    val result = onAccountCleared?.invoke(uid)
                    if (result != null && result !is OperationResult.Success) return@withContext cleanupFailure()
                }
                while (isCleanupPending()) {
                    val before = getPendingCleanupUid()
                    if (before.isNotEmpty() && authAdapter.activeUid() == before && !signOutConfirmed()) {
                        return@withContext cleanupFailure()
                    }
                    val result = onAccountCleared?.invoke(before)
                    if (result !is OperationResult.Success ||
                        (isCleanupPending() && before == getPendingCleanupUid())) return@withContext cleanupFailure()
                }
                OperationResult.Success(Unit)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                cleanupFailure()
            }
        }

    /** A storage failure aborts the transition before changing either credential or generation. */
    private suspend fun recordObligations(accounts: List<String>): Boolean {
        return try {
            for (uid in accounts) markCleanupPending?.invoke(uid)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    /** The SDK can swallow a sign-out error. Keep its obligation until credentials are gone. */
    private fun signOutConfirmed(): Boolean {
        return try {
            authAdapter.signOut()
            authAdapter.activeUid() == null
        } catch (_: RuntimeException) {
            false
        }
    }

    /** Returns a retryable result if recording or finishing a cleanup obligation failed. */
    private fun cleanupFailure() = OperationResult.Unavailable("Account cleanup could not finish. Retry the transition.")

    /** Discards a late result and retires identities only if the SDK displaced the valid account. */
    private suspend fun staleResult(captured: SessionSnapshot): OperationResult<Nothing>? {
        if (currentSession() == captured) return null
        val active = authAdapter.activeUid()
        if (active != null && active != currentSession().uid) {
            if (retire(currentSession().uid ?: captured.uid, signOutSdk = true) !is OperationResult.Success) {
                return cleanupFailure()
            }
        }
        return OperationResult.Denied("Session changed during authentication.")
    }
}
