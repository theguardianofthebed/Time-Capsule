package com.example.timecapsule.data.notification

import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
 * 1. Encoded Identity Keying: Keys entries using structured URL encoding (`uid:<uid>:capsule:<capsuleId>`)
 *    to prevent delimiter collision or ambiguity across UIDs and capsule IDs. Stores NO message content,
 *    photos, sender identity, user email, location, or tokens.
 * 2. Durable Atomic Persistence: Reuses [Files.move] with [StandardCopyOption.ATOMIC_MOVE] and [StandardCopyOption.REPLACE_EXISTING].
 *    Claims return success ONLY after disk persistence succeeds, preserving previous valid state on storage failure.
 * 3. Fail-Closed Recovery: Preserves existing state and fails claims closed if persistent file is unreadable or corrupt.
 *
 * @param storeFile Durable file location for persistent markers.
 */
class NotificationAttemptStore(
    private val storeFile: File
) {

    private val lock = Any()
    private val inMemoryState = ConcurrentHashMap<String, AttemptState>()
    private var persistentLoadFailed = false

    init {
        loadFromFile()
    }

    /** Generates structured URL-encoded key for [accountUid] and [capsuleId]. */
    private fun makeKey(accountUid: String, capsuleId: String): String {
        val cleanUid = accountUid.trim()
        val cleanCapsuleId = capsuleId.trim()
        require(cleanUid.isNotBlank() && cleanCapsuleId.isNotBlank()) {
            "Account UID and capsule ID must not be blank for notification attempt store."
        }
        val encodedUid = URLEncoder.encode(cleanUid, "UTF-8")
        val encodedCapsule = URLEncoder.encode(cleanCapsuleId, "UTF-8")
        return "uid:$encodedUid:capsule:$encodedCapsule"
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
     * Reverts memory state if durable storage write fails.
     *
     * @return True if persistent record was saved successfully.
     */
    fun recordPendingPermission(accountUid: String, capsuleId: String): Boolean {
        val cleanUid = accountUid.trim()
        val cleanCapsuleId = capsuleId.trim()
        if (cleanUid.isBlank() || cleanCapsuleId.isBlank()) return false
        val key = makeKey(cleanUid, cleanCapsuleId)

        synchronized(lock) {
            if (persistentLoadFailed) return false
            val previousState = inMemoryState[key]
            if (previousState == AttemptState.ATTEMPTED) {
                return false
            }
            inMemoryState[key] = AttemptState.PENDING_PERMISSION
            val saved = saveToFile()
            if (!saved) {
                if (previousState != null) {
                    inMemoryState[key] = previousState
                } else {
                    inMemoryState.remove(key)
                }
            }
            return saved
        }
    }

    /**
     * Atomically claims a notification attempt for [accountUid] and [capsuleId].
     * Durable persistence must succeed before returning true. If persistence fails or store is unreadable,
     * preserves previous valid state and returns false.
     *
     * @return True if attempt claim was successfully persisted to durable storage; false otherwise.
     */
    fun atomicallyClaimAttempt(accountUid: String, capsuleId: String): Boolean {
        val cleanUid = accountUid.trim()
        val cleanCapsuleId = capsuleId.trim()
        if (cleanUid.isBlank() || cleanCapsuleId.isBlank()) return false
        val key = makeKey(cleanUid, cleanCapsuleId)

        synchronized(lock) {
            if (persistentLoadFailed) return false
            val previousState = inMemoryState[key] ?: AttemptState.ABSENT
            if (previousState == AttemptState.ATTEMPTED) {
                return false
            }

            inMemoryState[key] = AttemptState.ATTEMPTED
            val saved = saveToFile()
            if (!saved) {
                if (previousState != AttemptState.ABSENT) {
                    inMemoryState[key] = previousState
                } else {
                    inMemoryState.remove(key)
                }
                return false
            }
            return true
        }
    }

    /**
     * Restores an [AttemptState.ATTEMPTED] state back to [AttemptState.PENDING_PERMISSION]
     * if posting was definitively not attempted.
     */
    fun restorePendingPermissionIfClaimed(accountUid: String, capsuleId: String): Boolean {
        val cleanUid = accountUid.trim()
        val cleanCapsuleId = capsuleId.trim()
        if (cleanUid.isBlank() || cleanCapsuleId.isBlank()) return false
        val key = makeKey(cleanUid, cleanCapsuleId)

        synchronized(lock) {
            if (persistentLoadFailed) return false
            val previousState = inMemoryState[key] ?: return false
            if (previousState != AttemptState.ATTEMPTED) return false

            inMemoryState[key] = AttemptState.PENDING_PERMISSION
            val saved = saveToFile()
            if (!saved) {
                inMemoryState[key] = AttemptState.ATTEMPTED
                return false
            }
            return true
        }
    }

    /**
     * Clears recorded markers for [accountUid] during account cleanup.
     */
    fun clearAccount(accountUid: String) {
        val cleanUid = accountUid.trim()
        if (cleanUid.isBlank()) return
        val encodedUidPrefix = "uid:" + URLEncoder.encode(cleanUid, "UTF-8") + ":capsule:"

        synchronized(lock) {
            val keysToRemove = inMemoryState.keys.filter { it.startsWith(encodedUidPrefix) }
            if (keysToRemove.isEmpty()) return

            val backup = HashMap(inMemoryState)
            for (k in keysToRemove) {
                inMemoryState.remove(k)
            }
            if (!saveToFile()) {
                inMemoryState.clear()
                inMemoryState.putAll(backup)
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
                persistentLoadFailed = true
            }
        }
    }

    private fun saveToFile(): Boolean {
        synchronized(lock) {
            val parent = storeFile.parentFile ?: return false
            if (!parent.isDirectory && !parent.mkdirs()) {
                return false
            }
            val temporary = try {
                File.createTempFile("attempt-", ".partial", parent)
            } catch (_: IOException) {
                return false
            }
            return try {
                temporary.outputStream().use { stream ->
                    val text = inMemoryState.entries.joinToString("\n") { "${it.key}=${it.value.name}" }
                    stream.write(text.toByteArray(Charsets.UTF_8))
                    stream.fd.sync()
                }
                Files.move(
                    temporary.toPath(),
                    storeFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
                true
            } catch (_: Exception) {
                false
            } finally {
                temporary.delete()
            }
        }
    }
}
