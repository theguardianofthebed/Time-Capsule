package com.example.timecapsule.data.session

import com.example.timecapsule.domain.session.SessionSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe implementation of [SessionTracker] using [AtomicLong] generation counters
 * and [MutableStateFlow] session states.
 */
class DefaultSessionTracker : SessionTracker {

    private val generationCounter = AtomicLong(1L)
    private val sessionState = MutableStateFlow(SessionSnapshot(uid = null, sessionGeneration = generationCounter.get()))

    override fun currentSession(): SessionSnapshot = sessionState.value

    override fun observeSession(): Flow<SessionSnapshot> = sessionState.asStateFlow()

    override fun startSession(uid: String?) {
        val nextGen = generationCounter.incrementAndGet()
        sessionState.value = SessionSnapshot(uid = uid, sessionGeneration = nextGen)
    }

    override fun clearSession() {
        startSession(null)
    }

    override fun isSessionValid(capturedSnapshot: SessionSnapshot): Boolean {
        val current = currentSession()
        return (current.uid == capturedSnapshot.uid &&
                current.sessionGeneration == capturedSnapshot.sessionGeneration)
    }
}
