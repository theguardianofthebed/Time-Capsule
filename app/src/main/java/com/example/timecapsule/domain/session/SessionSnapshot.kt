package com.example.timecapsule.domain.session

/**
 * Immutable session identity snapshot tracking the authenticated user [uid]
 * and a monotonically increasing [sessionGeneration] counter.
 */
data class SessionSnapshot(
    val uid: String?,
    val sessionGeneration: Long = 0L,
) {
    /** Indicates whether an active authenticated user session exists. */
    val isAuthenticated: Boolean
        get() = !uid.isNullOrBlank()
}
