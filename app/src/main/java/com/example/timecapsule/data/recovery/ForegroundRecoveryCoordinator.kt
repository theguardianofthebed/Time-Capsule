package com.example.timecapsule.data.recovery

import com.example.timecapsule.data.CapsuleRepository
import com.example.timecapsule.data.session.SessionTracker
import com.example.timecapsule.domain.model.Availability
import com.example.timecapsule.domain.model.RecoveredPublication
import com.example.timecapsule.domain.model.SendStatus
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Result summary data class for foreground recovery checks.
 *
 * @property sessionSnapshot The active session snapshot when recovery was executed.
 * @property publicationRecoveryResult Typed outcome of recovering saved publication states.
 * @property unlockResults Map of capsule ID to unlock verification results for known capsule IDs.
 */
data class ForegroundRecoverySummary(
    val sessionSnapshot: SessionSnapshot,
    val publicationRecoveryResult: OperationResult<List<RecoveredPublication>>,
    val unlockResults: Map<String, OperationResult<Availability>> = emptyMap()
)

/**
 * Coordinates and coalesces foreground recovery operations across app-open, return to foreground,
 * and explicit UI retry actions.
 *
 * Single-Flight & Session Security Guarantees:
 * 1. Single-Flight Coalescing: Uses a short registration [Mutex] and identity-safe `active.remove(key, started)`
 *    to guarantee that overlapping identical keys execute exactly one underlying operation.
 * 2. Immediate Completion Safety: Lazy [Deferred] creation and registration before execution prevent races
 *    where immediate coroutine completions modify un-registered map entries.
 * 3. Fresh Subsequent Retries: Completed or failed flight entries are automatically removed upon completion
 *    so subsequent retries acquire fresh server and location evidence.
 * 4. Waiter Isolation: Cancellation of one waiter's coroutine does not cancel other waiters or the underlying job.
 * 5. Session Drift Protection: Binds each operation and aggregate check to an immutable [SessionSnapshot].
 *    Validates session state before starting, during suspension points, and before dispatch.
 * 6. Non-Blocking Lifecycle: Does not hold strong references to Activity or Fragment instances.
 */
class ForegroundRecoveryCoordinator(
    private val repository: CapsuleRepository,
    private val sessionTracker: SessionTracker,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher),
    private val responseTimeoutMillis: Long = 15_000L
) {
    private data class RecoveryKey(val uid: String, val sessionGeneration: Long)
    private data class UnlockKey(val uid: String, val sessionGeneration: Long, val capsuleId: String)
    private data class ReconcileKey(val uid: String, val sessionGeneration: Long, val capsuleId: String)

    private val registrationMutex = Mutex()
    private val activeRecovery = ConcurrentHashMap<RecoveryKey, Deferred<OperationResult<List<RecoveredPublication>>>>()
    private val activeUnlock = ConcurrentHashMap<UnlockKey, Deferred<OperationResult<Availability>>>()
    private val activeReconcile = ConcurrentHashMap<ReconcileKey, Deferred<OperationResult<SendStatus>>>()

    /**
     * Reusable single-flight registration helper using a short registration [Mutex],
     * lazy [Deferred] creation, registration before execution, and identity-safe map removal.
     */
    private suspend fun <K : Any, V : Any> startSingleFlight(
        map: ConcurrentHashMap<K, Deferred<V>>,
        key: K,
        action: suspend () -> V
    ): Deferred<V> {
        val existing = map[key]
        if (existing != null && existing.isActive) {
            return existing
        }
        return registrationMutex.withLock {
            val current = map[key]
            if (current != null && current.isActive) {
                current
            } else {
                val started = scope.async(ioDispatcher, start = CoroutineStart.LAZY) {
                    action()
                }
                map[key] = started
                started.invokeOnCompletion {
                    map.remove(key, started)
                }
                started.start()
                started
            }
        }
    }

    /**
     * Executes or joins an in-flight send recovery for the active session (uid, sessionGeneration).
     *
     * @param targetSession Optional explicitly bound session. Defaults to current session.
     * @return Typed result containing recovered publication records, or [OperationResult.Denied] if session changed.
     */
    suspend fun recoverPublications(
        targetSession: SessionSnapshot = sessionTracker.currentSession()
    ): OperationResult<List<RecoveredPublication>> = withContext(ioDispatcher) {
        val uid = targetSession.uid
        if (!targetSession.isAuthenticated || uid.isNullOrBlank() || !sessionTracker.isSessionValid(targetSession)) {
            return@withContext OperationResult.Denied("Authentication required for publication recovery.")
        }
        val key = RecoveryKey(uid, targetSession.sessionGeneration)

        val deferred = startSingleFlight(activeRecovery, key) {
            if (!sessionTracker.isSessionValid(targetSession)) {
                return@startSingleFlight OperationResult.Denied("Account session changed before publication recovery started.")
            }
            val res = try {
                withTimeoutOrNull(responseTimeoutMillis) {
                    repository.recoverPublications(targetSession)
                } ?: OperationResult.Unavailable("Publication recovery timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                OperationResult.Unavailable("Publication recovery failed: ${e.message}")
            }

            if (!sessionTracker.isSessionValid(targetSession)) {
                OperationResult.Denied("Account session changed during publication recovery execution.")
            } else {
                res
            }
        }

        val result = try {
            deferred.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OperationResult.Unavailable("Publication recovery failed: ${e.message}")
        }

        if (!sessionTracker.isSessionValid(targetSession)) {
            return@withContext OperationResult.Denied("Account session changed during publication recovery.")
        }

        result
    }

    /**
     * Executes or joins an in-flight recipient unlock verification for [capsuleId] under the active session.
     * Removes completed flight so subsequent retries acquire fresh server and location evidence.
     *
     * @param capsuleId The known capsule ID to verify unlock for.
     * @param targetSession Optional explicitly bound session. Defaults to current session.
     * @return Typed availability result.
     */
    suspend fun verifyUnlock(
        capsuleId: String,
        targetSession: SessionSnapshot = sessionTracker.currentSession()
    ): OperationResult<Availability> = withContext(ioDispatcher) {
        val cleanId = capsuleId.trim()
        if (cleanId.isBlank()) {
            return@withContext OperationResult.Invalid("Capsule ID must not be blank for unlock verification.")
        }
        val uid = targetSession.uid
        if (!targetSession.isAuthenticated || uid.isNullOrBlank() || !sessionTracker.isSessionValid(targetSession)) {
            return@withContext OperationResult.Denied("Authentication required for unlock verification.")
        }
        val key = UnlockKey(uid, targetSession.sessionGeneration, cleanId)

        val deferred = startSingleFlight(activeUnlock, key) {
            if (!sessionTracker.isSessionValid(targetSession)) {
                return@startSingleFlight OperationResult.Denied("Account session changed before unlock verification started.")
            }
            val res = try {
                withTimeoutOrNull(responseTimeoutMillis) {
                    repository.verifyUnlock(cleanId, targetSession)
                } ?: OperationResult.Unavailable("Unlock verification timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                OperationResult.Unavailable("Unlock verification failed: ${e.message}")
            }

            if (!sessionTracker.isSessionValid(targetSession)) {
                OperationResult.Denied("Account session changed during unlock verification execution.")
            } else {
                res
            }
        }

        val result = try {
            deferred.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OperationResult.Unavailable("Unlock verification failed: ${e.message}")
        }

        if (!sessionTracker.isSessionValid(targetSession)) {
            return@withContext OperationResult.Denied("Account session changed during unlock verification.")
        }

        result
    }

    /**
     * Executes or joins an in-flight send outcome reconciliation for [capsuleId] under the active session.
     *
     * @param capsuleId The capsule ID to reconcile send outcome for.
     * @param targetSession Optional explicitly bound session. Defaults to current session.
     * @return Typed send status result.
     */
    suspend fun reconcile(
        capsuleId: String,
        targetSession: SessionSnapshot = sessionTracker.currentSession()
    ): OperationResult<SendStatus> = withContext(ioDispatcher) {
        val cleanId = capsuleId.trim()
        if (cleanId.isBlank()) {
            return@withContext OperationResult.Invalid("Capsule ID must not be blank for reconciliation.")
        }
        val uid = targetSession.uid
        if (!targetSession.isAuthenticated || uid.isNullOrBlank() || !sessionTracker.isSessionValid(targetSession)) {
            return@withContext OperationResult.Denied("Authentication required for send reconciliation.")
        }
        val key = ReconcileKey(uid, targetSession.sessionGeneration, cleanId)

        val deferred = startSingleFlight(activeReconcile, key) {
            if (!sessionTracker.isSessionValid(targetSession)) {
                return@startSingleFlight OperationResult.Denied("Account session changed before reconciliation started.")
            }
            val res = try {
                withTimeoutOrNull(responseTimeoutMillis) {
                    repository.reconcile(cleanId, targetSession)
                } ?: OperationResult.Unavailable("Send reconciliation timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                OperationResult.Unavailable("Send reconciliation failed: ${e.message}")
            }

            if (!sessionTracker.isSessionValid(targetSession)) {
                OperationResult.Denied("Account session changed during reconciliation execution.")
            } else {
                res
            }
        }

        val result = try {
            deferred.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OperationResult.Unavailable("Send reconciliation failed: ${e.message}")
        }

        if (!sessionTracker.isSessionValid(targetSession)) {
            return@withContext OperationResult.Denied("Account session changed during send reconciliation.")
        }

        result
    }

    /**
     * Entry point for foreground check (app-open, return to foreground, or S-03 list refresh / S-07 detail open in Issue #21).
     * Binds aggregate request to [targetSession] and runs publication recovery and unlock checks concurrently.
     *
     * @param knownCapsuleIds List of known capsule IDs to verify unlock for.
     * @param targetSession Explicitly bound session. Defaults to current session.
     * @return [ForegroundRecoverySummary] containing recovery results.
     */
    suspend fun checkForeground(
        knownCapsuleIds: List<String> = emptyList(),
        targetSession: SessionSnapshot = sessionTracker.currentSession()
    ): ForegroundRecoverySummary = withContext(ioDispatcher) {
        if (!targetSession.isAuthenticated || targetSession.uid.isNullOrBlank() || !sessionTracker.isSessionValid(targetSession)) {
            return@withContext ForegroundRecoverySummary(
                sessionSnapshot = targetSession,
                publicationRecoveryResult = OperationResult.Denied("Authentication required for foreground recovery.")
            )
        }

        val pubDeferred = async { recoverPublications(targetSession) }
        val unlockDeferreds = knownCapsuleIds.mapNotNull { id ->
            val cleanId = id.trim()
            if (cleanId.isNotBlank()) {
                cleanId to async { verifyUnlock(cleanId, targetSession) }
            } else null
        }

        val pubResult = pubDeferred.await()

        if (!sessionTracker.isSessionValid(targetSession)) {
            return@withContext ForegroundRecoverySummary(
                sessionSnapshot = targetSession,
                publicationRecoveryResult = OperationResult.Denied("Account session changed during foreground recovery.")
            )
        }

        val unlockMap = mutableMapOf<String, OperationResult<Availability>>()
        for ((id, deferred) in unlockDeferreds) {
            if (!sessionTracker.isSessionValid(targetSession)) {
                return@withContext ForegroundRecoverySummary(
                    sessionSnapshot = targetSession,
                    publicationRecoveryResult = OperationResult.Denied("Account session changed during foreground recovery.")
                )
            }
            unlockMap[id] = deferred.await()
        }

        if (!sessionTracker.isSessionValid(targetSession)) {
            return@withContext ForegroundRecoverySummary(
                sessionSnapshot = targetSession,
                publicationRecoveryResult = OperationResult.Denied("Account session changed during foreground recovery.")
            )
        }

        ForegroundRecoverySummary(
            sessionSnapshot = targetSession,
            publicationRecoveryResult = pubResult,
            unlockResults = unlockMap
        )
    }

    /**
     * Non-blocking trigger for foreground recovery from Activity/Fragment lifecycle events.
     * Binds request to original captured session snapshot and rejects stale execution.
     *
     * @param knownCapsuleIds List of known capsule IDs.
     */
    fun triggerForegroundRecovery(
        knownCapsuleIds: List<String> = emptyList()
    ) {
        val capturedSession = sessionTracker.currentSession()
        if (!capturedSession.isAuthenticated || capturedSession.uid.isNullOrBlank()) return
        scope.launch(ioDispatcher) {
            if (!sessionTracker.isSessionValid(capturedSession)) return@launch
            checkForeground(knownCapsuleIds, capturedSession)
        }
    }
}
