package com.example.timecapsule.domain.result

/**
 * Strongly typed operation outcome for application data and repository requests.
 * Replaces generic exceptions across architecture boundaries.
 */
sealed interface OperationResult<out T> {

    /** Successful operation carrying validated result [data]. */
    data class Success<out T>(val data: T) : OperationResult<T>

    /** Invalid request input or parameter violation with descriptive [message]. */
    data class Invalid(val message: String) : OperationResult<Nothing>

    /** Unauthenticated or unauthorized access attempt with descriptive [message]. */
    data class Denied(val message: String) : OperationResult<Nothing>

    /** External dependency, network, or configuration unavailable with [message]. */
    data class Unavailable(val message: String) : OperationResult<Nothing>

    /**
     * Ambiguous operation outcome where a remote write was initiated for [capsuleId]
     * but its final server status cannot be confirmed immediately.
     */
    data class OutcomeUnknown(val capsuleId: String, val message: String) : OperationResult<Nothing>
}
