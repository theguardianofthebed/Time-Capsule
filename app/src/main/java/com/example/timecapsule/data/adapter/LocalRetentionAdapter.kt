package com.example.timecapsule.data.adapter

/**
 * Replaceable adapter boundary for UID-scoped local app-private storage and DataStore markers.
 */
interface LocalRetentionAdapter {
    /** Clears account-scoped retained content for [uid]. */
    suspend fun clearAccountData(uid: String)
}
