package com.example.timecapsule.data.adapter

import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.domain.model.OriginalPhotoFormat
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot
import java.time.Instant

/**
 * Verified retained content record holding text, photo bytes, and server unlock evidence.
 *
 * @property uid Authenticated recipient UID owning this retained content.
 * @property capsuleId Unique capsule identifier.
 * @property text Retained capsule message text, if present.
 * @property photoBytes Retained capsule photo binary bytes, if present.
 * @property photoFormat Format of the retained photo (JPEG or PNG), if present.
 * @property verifiedUnlockedAt Authoritative server unlock timestamp recorded when verified.
 */
data class RetainedContent(
    val uid: String,
    val capsuleId: String,
    val text: String?,
    val photoBytes: ByteArray?,
    val photoFormat: OriginalPhotoFormat?,
    val verifiedUnlockedAt: Instant
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RetainedContent
        if (uid != other.uid) return false
        if (capsuleId != other.capsuleId) return false
        if (text != other.text) return false
        if (photoBytes != null) {
            if (other.photoBytes == null) return false
            if (!photoBytes.contentEquals(other.photoBytes)) return false
        } else if (other.photoBytes != null) return false
        if (photoFormat != other.photoFormat) return false
        if (verifiedUnlockedAt != other.verifiedUnlockedAt) return false
        return true
    }

    override fun hashCode(): Int {
        var result = uid.hashCode()
        result = 31 * result + capsuleId.hashCode()
        result = 31 * result + (text?.hashCode() ?: 0)
        result = 31 * result + (photoBytes?.contentHashCode() ?: 0)
        result = 31 * result + (photoFormat?.hashCode() ?: 0)
        result = 31 * result + verifiedUnlockedAt.hashCode()
        return result
    }
}

/**
 * Lightweight account-scoped retention summary metadata for list enumeration without bulk loading protected bytes.
 */
data class RetainedSummary(
    val uid: String,
    val capsuleId: String,
    val verifiedUnlockedAt: Instant,
    val senderUid: String? = null,
    val recipientUid: String? = null,
    val lockMode: LockMode? = null,
    val unlockAt: Instant? = null,
    val title: String? = null,
    val senderDisplayName: String? = null,
    val recipientDisplayName: String? = null,
    val createdAt: Instant? = null,
    val hasText: Boolean = false,
    val hasPhoto: Boolean = false,
    val isContentAvailable: Boolean = true
)

/**
 * Replaceable adapter boundary for UID-scoped local app-private storage and DataStore markers.
 */
interface LocalRetentionAdapter {
    /**
     * Persists verified recipient unlock marker with server timestamp and metadata independently of content download success.
     */
    suspend fun saveVerifiedUnlockMarker(
        targetSession: SessionSnapshot,
        capsuleId: String,
        verifiedUnlockedAt: Instant,
        title: String? = null,
        lockMode: LockMode? = null,
        senderUid: String? = null,
        recipientUid: String? = null,
        senderDisplayName: String? = null,
        recipientDisplayName: String? = null,
        createdAt: Instant? = null,
        hasText: Boolean = false,
        hasPhoto: Boolean = false
    ): OperationResult<Unit> = OperationResult.Unavailable("Save verified unlock marker is not implemented in this adapter.")

    /**
     * Persists verified recipient unlocked content with server timestamp, metadata, and session snapshot.
     */
    suspend fun saveRetainedContent(
        targetSession: SessionSnapshot,
        capsuleId: String,
        verifiedUnlockedAt: Instant,
        text: String?,
        photoBytes: ByteArray?,
        photoFormat: OriginalPhotoFormat?,
        title: String? = null,
        lockMode: LockMode? = null,
        senderDisplayName: String? = null,
        recipientDisplayName: String? = null,
        createdAt: Instant? = null
    ): OperationResult<Unit>

    /**
     * Reads verified retained content for the active account if marker and content files are complete.
     */
    suspend fun readRetainedContent(
        targetSession: SessionSnapshot,
        capsuleId: String
    ): OperationResult<RetainedContent>

    /**
     * Lists lightweight retention metadata for [targetSession] without bulk loading protected text or photo bytes.
     */
    suspend fun listVerifiedRecipientEntries(
        targetSession: SessionSnapshot
    ): OperationResult<List<RetainedSummary>> {
        return OperationResult.Unavailable("List verified recipient entries is not implemented in this adapter.")
    }

    /** Clears account-scoped retained content for [uid]. */
    suspend fun clearAccountData(uid: String)

    /** Clears all retained content across all accounts during process-wide reset. */
    suspend fun clearAllData()
}
