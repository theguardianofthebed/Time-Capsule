package com.example.timecapsule.domain.result

/** User-facing reasons supplied by the publication stage that actually failed. */
enum class PublicationFailure(val guidance: String) {
    PHOTO_READ("The saved photo could not be read. Check the original photo before retrying."),
    PHOTO_DENIED("Photo access was denied. Check account access before retrying."),
    PHOTO_UPLOAD("Photo upload unavailable. Check your connection and retry the same capsule."),
    BACKEND_REJECTED("The server rejected publication. Review the capsule or retry unchanged.")
}

/**
 * Strongly typed operation outcome for application data and repository requests.
 * Replaces generic exceptions across architecture boundaries.
 */
sealed interface OperationResult<out T> {
    val publicationFailure: PublicationFailure? get() = null

    /** Successful operation carrying validated result [data]. */
    data class Success<out T>(val data: T) : OperationResult<T>

    /** Invalid request input or parameter violation with descriptive [message]. */
    data class Invalid(val message: String, override val publicationFailure: PublicationFailure? = null) : OperationResult<Nothing>

    /** Unauthenticated or unauthorized access attempt with descriptive [message]. */
    data class Denied(val message: String, override val publicationFailure: PublicationFailure? = null) : OperationResult<Nothing>

    /** External dependency, network, or configuration unavailable with [message]. */
    data class Unavailable(val message: String, override val publicationFailure: PublicationFailure? = null) : OperationResult<Nothing>

    /**
     * Ambiguous operation outcome where a remote write was initiated for [capsuleId]
     * but its final server status cannot be confirmed immediately.
     */
    data class OutcomeUnknown(val capsuleId: String, val message: String) : OperationResult<Nothing>
}
