package com.example.timecapsule.data.session

import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot

/**
 * Guards asynchronous repository operations by validating session identity and generation
 * before and after operation execution.
 */
class SessionGuard(
    private val sessionTracker: SessionTracker
) {

    /**
     * Executes [operation] and verifies that [capturedSnapshot] remains valid.
     * Returns [OperationResult.Denied] if the session changed or signed out during execution.
     */
    suspend fun <T> runGuarded(
        capturedSnapshot: SessionSnapshot,
        operation: suspend () -> OperationResult<T>
    ): OperationResult<T> {
        if (!sessionTracker.isSessionValid(capturedSnapshot)) {
            return OperationResult.Denied("Stale session invalidation: session changed before operation started")
        }

        val result = operation()

        if (!sessionTracker.isSessionValid(capturedSnapshot)) {
            return OperationResult.Denied("Stale session invalidation: session changed during operation execution")
        }

        return result
    }
}
