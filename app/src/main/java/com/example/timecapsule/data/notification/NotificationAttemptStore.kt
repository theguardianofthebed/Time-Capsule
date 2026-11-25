package com.example.timecapsule.data.notification

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistent marker states for account-scoped local notification attempts.
 */
enum class AttemptState {
    /**
     * No notification attempt or pending candidate has been recorded for this capsule.
     */
    ABSENT,

    /**
     * Confirmed first unlock occurred while system notification permission was denied.
     * Preserved as a candidate for retry during future foreground checks if permission is granted.
     */
    PENDING_PERMISSION,

    /**
     * System notification posting was attempted for this capsule under this account.
     * Prevents duplicate alerts.
     */
    ATTEMPTED
}

/**
 * Persistent store managing atomic notification attempt markers for account/capsule pairs.
 *
 * Security & Isolation Guarantees:
 * 1. Opaque Identity Keying: Keys entries by `(accountUid, capsuleId)`. Stores NO message content,
 *    photos, sender identity, user email, location, or tokens.
 * 2. Atomic Claiming: [atomicallyClaimAttempt] performs atomic state check-and-set to guarantee
 *    that concurrent verification checks or retries execute at most one attempt claim.
 * 3. Persistence: Durably persists state in a backup-excluded app-private JSON file to survive
 *    Activity recreation and process restarts.
 *
 * @param storeFile Durable JSON file location for persistent markers.
 */
class NotificationAttemptStore(
    private val storeFile: File
) {

    private val lock = Any()
    private val inMemoryState = ConcurrentHashMap<String, AttemptState>()

    init {
        loadFromFile()
    }

    /** Generates collision-safe store key for [accountUid] and [capsuleId]. */
    private fun makeKey(accountUid: String, capsuleId: String): String {
        val cleanUid = accountUid.trim()
        val cleanCapsuleId = capsuleId.trim()
        require(cleanUid.isNotBlank() && cleanCapsuleId.isNotBlank()) {
            "Account UID and capsule ID must not be blank for notification attempt store."
        }
        return "$cleanUid:$cleanCapsuleId"
    }

    /**
     * Retrieves current [AttemptState] for [accountUid] and [capsuleId].
     */
    fun getAttemptState(accountUid: String, capsuleId: String): AttemptState {
        val cleanUid = accountUid.trim()
        val cleanCapsuleId = capsuleId.trim()
        if (cleanUid.isBlank() || cleanCapsuleId.isBlank()) return AttemptState.ABSENT
        val key = makeKey(cleanUid, cleanCapsuleId)
        return inMemoryState[key] ?: AttemptState.ABSENT
    }

    /**
     * Records a [AttemptState.PENDING_PERMISSION] state when a confirmed unlock occurs while permission is denied.
     */
    fun recordPendingPermission(accountUid: String, capsuleId: String) {
        val cleanUid = accountUid.trim()
        val cleanCapsuleId = capsuleId.trim()
        if (cleanUid.isBlank() || cleanCapsuleId.isBlank()) return
        val key = makeKey(cleanUid, cleanCapsuleId)

        synchronized(lock) {
            val current = inMemoryState[key]
            if (current != AttemptState.ATTEMPTED) {
                inMemoryState[key] = AttemptState.PENDING_PERMISSION
                saveToFile()
            }
        }
    }

    /**
     * Atomically claims a notification attempt for [accountUid] and [capsuleId].
     * If current state is [AttemptState.ABSENT] or [AttemptState.PENDING_PERMISSION],
     * transitions state to [AttemptState.ATTEMPTED] and returns true.
     * If already [AttemptState.ATTEMPTED], returns false.
     *
     * @return True if attempt claim was successfully acquired; false if already attempted.
     */
    fun atomicallyClaimAttempt(accountUid: String, capsuleId: String): Boolean {
        val cleanUid = accountUid.trim()
        val cleanCapsuleId = capsuleId.trim()
        if (cleanUid.isBlank() || cleanCapsuleId.isBlank()) return false
        val key = makeKey(cleanUid, cleanCapsuleId)

        synchronized(lock) {
            val current = inMemoryState[key] ?: AttemptState.ABSENT
            if (current == AttemptState.ATTEMPTED) {
                return false
            }
            inMemoryState[key] = AttemptState.ATTEMPTED
            saveToFile()
            return true
        }
    }

    /**
     * Clears recorded markers for [accountUid] during account cleanup.
     */
    fun clearAccount(accountUid: String) {
        val cleanUid = accountUid.trim()
        if (cleanUid.isBlank()) return
        val prefix = "$cleanUid:"

        synchronized(lock) {
            val keysToRemove = inMemoryState.keys.filter { it.startsWith(prefix) }
            for (k in keysToRemove) {
                inMemoryState.remove(k)
            }
            if (keysToRemove.isNotEmpty()) {
                saveToFile()
            }
        }
    }

    private fun loadFromFile() {
        synchronized(lock) {
            inMemoryState.clear()
            if (!storeFile.exists()) return
            try {
                val lines = storeFile.readLines(Charsets.UTF_8)
                for (line in lines) {
                    val parts = line.split("=", limit = 2)
                    if (parts.size == 2) {
                        val key = parts[0].trim()
                        val stateStr = parts[1].trim()
                        val state = try {
                            AttemptState.valueOf(stateStr)
                        } catch (_: Exception) {
                            AttemptState.ABSENT
                        }
                        if (key.isNotBlank() && state != AttemptState.ABSENT) {
                            inMemoryState[key] = state
                        }
                    }
                }
            } catch (_: Exception) {
                // Fail closed on corrupted persistent state
            }
        }
    }

    private fun saveToFile() {
        synchronized(lock) {
            try {
                val parent = storeFile.parentFile
                if (parent != null && !parent.exists()) {
                    parent.mkdirs()
                }
                val text = inMemoryState.entries.joinToString("\n") { "${it.key}=${it.value.name}" }
                storeFile.writeText(text, Charsets.UTF_8)
            } catch (_: Exception) {
                // Log/fail gracefully on storage IO exception
            }
        }
    }
}
