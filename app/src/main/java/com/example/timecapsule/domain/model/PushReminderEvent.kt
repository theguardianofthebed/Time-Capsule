package com.example.timecapsule.domain.model

import java.security.MessageDigest
import java.time.Instant

/** A content-free reminder to check a TIME capsule, never proof that it is unlocked. */
class PushReminderEvent private constructor(
    val eventId: String,
    val capsuleId: String,
    val accountUid: String,
    val bindingId: String,
    val notBefore: Instant,
    val expiresAt: Instant
) {
    /** Binds a deduplication claim to every accepted field, detecting reused IDs with changed data. */
    fun fingerprint(): String {
        val fields = listOf("1", "TIME_REMINDER", eventId, capsuleId, accountUid, bindingId,
            notBefore.toString(), expiresAt.toString())
        val canonical = fields.joinToString("") { "${it.toByteArray(Charsets.UTF_8).size}:$it" }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        private val keys = setOf("schemaVersion", "eventType", "eventId", "capsuleId",
            "accountUid", "bindingId", "notBefore", "expiresAt")
        private val identifier = Regex("[A-Za-z0-9_-]{1,256}")

        /**
         * Accepts only the versioned allow-list. Unknown fields, including private previews, fail closed.
         * The 256-character identifier cap is this adapter's input bound, not an FCM protocol claim.
         * Returns null without logging untrusted payload values.
         */
        fun parse(data: Map<String, String>): PushReminderEvent? {
            if (data.keys != keys || data["schemaVersion"] != "1" ||
                data["eventType"] != "TIME_REMINDER") return null
            val ids = listOf("eventId", "capsuleId", "accountUid", "bindingId").map { data[it] ?: "" }
            if (ids.any { !identifier.matches(it) }) return null
            val start = data["notBefore"] ?: return null
            val end = data["expiresAt"] ?: return null
            if (start.length > 40 || end.length > 40) return null
            return try {
                val notBefore = Instant.parse(start)
                val expiresAt = Instant.parse(end)
                if (notBefore >= expiresAt) null
                else PushReminderEvent(ids[0], ids[1], ids[2], ids[3], notBefore, expiresAt)
            } catch (_: java.time.format.DateTimeParseException) {
                null
            }
        }
    }
}
