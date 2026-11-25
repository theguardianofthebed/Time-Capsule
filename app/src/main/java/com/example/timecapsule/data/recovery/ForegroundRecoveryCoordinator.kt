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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
 * Concurrency & Session Rules:
 * 1. Coalesces overlapping publication recovery for the same (uid, sessionGeneration).
 * 2. Coalesces overlapping recipient unlock checks for the same (uid, sessionGeneration, capsuleId).
 * 3. Coalesces overlapping send outcome reconciliation for the same (uid, sessionGeneration, capsuleId).
 * 4. Removes completed flight entries upon completion (including failures) so subsequent retries
 *    acquire fresh server and location evidence.
 * 5. Revalidates session tracker before returning results to caller and discards stale callbacks.
 * 6. Does not hold strong references to Activity or Fragment instances.
 */
class ForegroundRecoveryCoordinator(
    private val repository: CapsuleRepository,
    private val sessionTracker: SessionTracker,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher)
) {
    private data class RecoveryKey(val uid: String, val sessionGeneration: Long)
    private data class UnlockKey(val uid: String, val sessionGeneration: Long, val capsuleId: String)
    private data class ReconcileKey(val uid: String, val sessionGeneration: Long, val capsuleId: String)

    private val activeRecovery = ConcurrentHashMap<RecoveryKey, Deferred<OperationResult<List<RecoveredPublication>>>>()
    private val activeUnlock = ConcurrentHashMap<UnlockKey, Deferred<OperationResult<Availability>>>()
    private val activeReconcile = ConcurrentHashMap<ReconcileKey, Deferred<OperationResult<SendStatus>>>()

    /**
     * Executes or joins an in-flight send recovery for the active session (uid, sessionGeneration).
     *
     * @return Typed result containing recovered publication records, or [OperationResult.Denied] if session changed.
     */
    suspend fun recoverPublications(): OperationResult<List<RecoveredPublication>> = withContext(ioDispatcher) {
        val captured = sessionTracker.currentSession()
        val uid = captured.uid
        if (!captured.isAuthenticated || uid.isNullOrBlank()) {
            return@withContext OperationResult.Denied("Authentication required for publication recovery.")
        }
        val key = RecoveryKey(uid, captured.sessionGeneration)

        val deferred = activeRecovery.computeIfAbsent(key) {
            scope.async {
                repository.recoverPublications()
            }.also { d ->
                d.invokeOnCompletion { activeRecovery.remove(key, d) }
            }
        }

        val result = try {
            deferred.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OperationResult.Unavailable("Publication recovery failed: ${e.message}")
        }

        if (!sessionTracker.isSessionValid(captured)) {
            return@withContext OperationResult.Denied("Account session changed during publication recovery.")
        }

        result
    }

    /**
     * Executes or joins an in-flight recipient unlock verification for [capsuleId] under the active session.
     * Removes completed flight so subsequent retries acquire fresh server and location evidence.
     *
     * @param capsuleId The known capsule ID to verify unlock for.
     * @return Typed availability result.
     */
    suspend fun verifyUnlock(capsuleId: String): OperationResult<Availability> = withContext(ioDispatcher) {
        val cleanId = capsuleId.trim()
        if (cleanId.isBlank()) {
            return@withContext OperationResult.Invalid("Capsule ID must not be blank for unlock verification.")
        }
        val captured = sessionTracker.currentSession()
        val uid = captured.uid
        if (!captured.isAuthenticated || uid.isNullOrBlank()) {
            return@withContext OperationResult.Denied("Authentication required for unlock verification.")
        }
        val key = UnlockKey(uid, captured.sessionGeneration, cleanId)

        val deferred = activeUnlock.computeIfAbsent(key) {
            scope.async {
                repository.verifyUnlock(cleanId)
            }.also { d ->
                d.invokeOnCompletion { activeUnlock.remove(key, d) }
            }
        }

        val result = try {
            deferred.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OperationResult.Unavailable("Unlock verification failed: ${e.message}")
        }

        if (!sessionTracker.isSessionValid(captured)) {
            return@withContext OperationResult.Denied("Account session changed during unlock verification.")
        }

        result
    }

    /**
     * Executes or joins an in-flight send outcome reconciliation for [capsuleId] under the active session.
     *
     * @param capsuleId The capsule ID to reconcile send outcome for.
     * @return Typed send status result.
     */
    suspend fun reconcile(capsuleId: String): OperationResult<SendStatus> = withContext(ioDispatcher) {
        val cleanId = capsuleId.trim()
        if (cleanId.isBlank()) {
            return@withContext OperationResult.Invalid("Capsule ID must not be blank for reconciliation.")
        }
        val captured = sessionTracker.currentSession()
        val uid = captured.uid
        if (!captured.isAuthenticated || uid.isNullOrBlank()) {
            return@withContext OperationResult.Denied("Authentication required for send reconciliation.")
        }
        val key = ReconcileKey(uid, captured.sessionGeneration, cleanId)

        val deferred = activeReconcile.computeIfAbsent(key) {
            scope.async {
                repository.reconcile(cleanId)
            }.also { d ->
                d.invokeOnCompletion { activeReconcile.remove(key, d) }
            }
        }

        val result = try {
            deferred.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OperationResult.Unavailable("Send reconciliation failed: ${e.message}")
        }

        if (!sessionTracker.isSessionValid(captured)) {
            return@withContext OperationResult.Denied("Account session changed during send reconciliation.")
        }

        result
    }

    /**
     * Entry point for foreground check (app-open, return to foreground, or S-03 list refresh / S-07 detail open in Issue #21).
     * Runs publication recovery and verifies unlock for any provided [knownCapsuleIds].
     *
     * @param knownCapsuleIds List of known capsule IDs to verify unlock for.
     * @return [ForegroundRecoverySummary] containing recovery results.
     */
    suspend fun checkForeground(
        knownCapsuleIds: List<String> = emptyList()
    ): ForegroundRecoverySummary = withContext(ioDispatcher) {
        val captured = sessionTracker.currentSession()
        if (!captured.isAuthenticated || captured.uid.isNullOrBlank()) {
            return@withContext ForegroundRecoverySummary(
                sessionSnapshot = captured,
                publicationRecoveryResult = OperationResult.Denied("Authentication required for foreground recovery.")
            )
        }

        val pubResult = recoverPublications()

        val unlockMap = mutableMapOf<String, OperationResult<Availability>>()
        for (id in knownCapsuleIds) {
            val cleanId = id.trim()
            if (cleanId.isNotBlank()) {
                unlockMap[cleanId] = verifyUnlock(cleanId)
            }
        }

        ForegroundRecoverySummary(
            sessionSnapshot = captured,
            publicationRecoveryResult = pubResult,
            unlockResults = unlockMap
        )
    }

    /**
     * Non-blocking trigger for foreground recovery from Activity/Fragment lifecycle events.
     * Launches on background scope and executes [checkForeground].
     *
     * @param knownCapsuleIds List of known capsule IDs.
     * @param onComplete Optional completion callback invoked only if session remains valid.
     */
    fun triggerForegroundRecovery(
        knownCapsuleIds: List<String> = emptyList(),
        onComplete: ((ForegroundRecoverySummary) -> Unit)? = null
    ) {
        val captured = sessionTracker.currentSession()
        if (!captured.isAuthenticated || captured.uid.isNullOrBlank()) return
        scope.launch(ioDispatcher) {
            val summary = checkForeground(knownCapsuleIds)
            if (sessionTracker.isSessionValid(captured)) {
                onComplete?.invoke(summary)
            }
        }
    }
}
