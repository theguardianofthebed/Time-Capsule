package com.example.timecapsule.domain.session

import kotlinx.coroutines.flow.Flow

/**
 * Read-only interface for observing and retrieving the current active [SessionSnapshot].
 */
interface SessionProvider {

    /** Returns the current active session snapshot synchronously. */
    fun currentSession(): SessionSnapshot

    /** Observes session snapshot changes over time. */
    fun observeSession(): Flow<SessionSnapshot>
}
