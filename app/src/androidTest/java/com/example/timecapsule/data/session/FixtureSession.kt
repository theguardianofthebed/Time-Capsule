package com.example.timecapsule.data.session

import com.example.timecapsule.domain.session.SessionSnapshot

/** One stable fixture login for isolated file-format and media tests. */
internal fun fixtureSession(uid: String): StorageSession {
    val snapshot = SessionSnapshot(uid, 1L)
    return StorageSession(snapshot) { snapshot }
}
