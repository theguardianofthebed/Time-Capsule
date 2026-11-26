package com.example.timecapsule.data.session

import com.example.timecapsule.domain.session.SessionSnapshot
import java.io.IOException

/** Carries an operation's original identity to disk, including across dispatcher and network waits. */
class StorageSession internal constructor(
    private val origin: SessionSnapshot,
    private val current: () -> SessionSnapshot
) {
    internal companion object {
        /** Serializes protected filesystem commits with generation changes in the process tracker. */
        val lock = Any()
    }

    /** Called under [lock] before opening or replacing a protected file. */
    internal fun requireOwner(uid: String) {
        check(Thread.holdsLock(lock))
        if (!origin.isAuthenticated || origin.uid != uid || current() != origin) {
            throw IOException("Account session changed before the private file could be saved.")
        }
    }
}
