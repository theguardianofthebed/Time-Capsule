package com.example.timecapsule.domain.model

import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId

/** Durable operation progress; a stored phase alone is never proof of server publication. */
enum class PublicationPhase {
    PREPARED, UPLOADING, PUBLISHING, OUTCOME_UNKNOWN, FAILED, SENT, ABANDONED
}

/**
 * Immutable submitted content, isolated from subsequent draft edits and provider URI permissions.
 * The digest includes immutable server fields and original photo identity, but excludes local file
 * names, server timestamps and mutable unlock state. Network work must follow durable storage.
 */
data class FrozenCapsuleOperation(
    val capsuleId: String,
    val senderUid: String,
    val recipientUid: String,
    val title: String?,
    val text: String,
    val lock: CapsuleLock,
    val creationZone: String,
    val photo: RetainedOriginalPhoto?,
    val snapshotDigest: String,
    val isOpaque: Boolean = false
) {
    /** Returns the one approved remote path, without exposing a public download URL. */
    val photoObjectPath: String?
        get() = photo?.let { "capsuleMedia/$senderUid/$capsuleId/original" }

    /** Validates restored immutable fields without rejecting a lock that expired after submission. */
    fun verify() {
        require(capsuleId.matches(Regex("[A-Za-z0-9_-]+"))) { "Invalid capsule ID." }
        require(senderUid.isNotBlank() && !senderUid.contains('/')) { "Invalid sender UID." }
        if (isOpaque) {
            require(snapshotDigest.isNotBlank()) { "Snapshot digest is required for opaque recovery." }
            return
        }
        require(recipientUid.isNotBlank()) { "Recipient is required." }
        require(text.isNotBlank() || photo != null) { "Message or photo is required." }
        ZoneId.of(creationZone)
        when (lock.mode) {
            LockMode.TIME -> require(lock.unlockAt != null && lock.location == null)
            LockMode.LOCATION -> require(lock.unlockAt == null && lock.location != null)
            LockMode.TIME_AND_LOCATION -> require(lock.unlockAt != null && lock.location != null)
        }
        lock.location?.let {
            require(it.latitude.isFinite() && it.latitude in -90.0..90.0)
            require(it.longitude.isFinite() && it.longitude in -180.0..180.0)
            require(it.radiusMeters == 150.0)
        }
        photo?.let {
            require(it.ownerUid == senderUid && it.capsuleId == capsuleId)
            require(OriginalPhotoLimits.acceptsSize(it.sizeBytes))
            require(it.sha256.matches(Regex("[a-f0-9]{64}")))
            require(it.privateFileName.matches(Regex("[a-f0-9-]{36}\\.original")))
        }
        require(snapshotDigest == calculateDigest()) { "Frozen content digest does not match." }
    }

    /**
     * Serializes a versioned, ordered UTF-8 payload using byte-length-prefixed values.
     * Null and empty strings are distinct; delimiters/newlines inside user text cannot collide.
     */
    fun canonicalPayload(): String = buildString {
        append("timecapsule-operation-v1\n")
        val location = lock.location
        val values = listOf(
            capsuleId, senderUid, recipientUid, title, text, lock.mode.name,
            lock.unlockAt?.epochSecond?.toString(), lock.unlockAt?.nano?.toString(),
            location?.latitude?.toString(), location?.longitude?.toString(),
            location?.radiusMeters?.toString(), creationZone, photoObjectPath,
            photo?.format?.mimeType, photo?.sizeBytes?.toString(), photo?.sha256
        )
        for (value in values) {
            if (value == null) {
                append("-1:\n")
            } else {
                // Reject malformed UTF-16 instead of hashing replacement characters silently.
                val bytes = Charsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value)).remaining()
                append(bytes).append(':').append(value).append('\n')
            }
        }
    }

    /** Recomputes the immutable content fingerprint for recovery and server reconciliation. */
    fun calculateDigest(): String = MessageDigest.getInstance("SHA-256")
        .digest(canonicalPayload().toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    companion object {
        /**
         * Validates and freezes one confirmed draft; repeated submission must retain its ID.
         * [now] is input validation only, not authoritative server time or unlock authorization.
         */
        fun freeze(
            draft: CapsuleDraft,
            senderUid: String,
            creationZone: String,
            now: Instant
        ): FrozenCapsuleOperation {
            draft.lock.unlockAt?.let { require(it.isAfter(now)) { "Select a future unlock time." } }
            return fromConfirmedDraft(draft, senderUid, creationZone)
        }

        /** Rebuilds confirmed content for comparison without changing an existing send's time. */
        fun fromConfirmedDraft(
            draft: CapsuleDraft,
            senderUid: String,
            creationZone: String
        ): FrozenCapsuleOperation {
            require(draft.isRecipientConfirmed) { "Confirm the recipient before sending." }
            require(draft.localPhotoUri.isNullOrBlank() || draft.originalPhoto != null) {
                "A provider URI alone is not a validated original photo."
            }
            val candidate = FrozenCapsuleOperation(
                draft.capsuleId, senderUid, draft.recipientUid, draft.title, draft.text,
                draft.lock, creationZone, draft.originalPhoto, ""
            )
            return candidate.copy(snapshotDigest = candidate.calculateDigest()).also { it.verify() }
        }
    }
}

/** A recoverable local record; phase is operational progress, not independent server evidence. */
data class FrozenOperationRecord(
    val operation: FrozenCapsuleOperation,
    val phase: PublicationPhase = PublicationPhase.PREPARED,
    val photoAcknowledged: Boolean = false,
    val hasUnresolvedWrite: Boolean = false,
    val terminalRejectionConfirmed: Boolean = false,
    val replacementDraftId: String? = null,
    val replacementPrepared: Boolean = false
)

/** Authenticated recovery state; completed operations never expose their raw compose snapshot. */
data class RecoveredPublication(
    val capsuleId: String,
    val phase: PublicationPhase,
    val snapshot: FrozenCapsuleOperation?,
    val canAbandon: Boolean,
    val clearedEditableDraft: Boolean = false
)
