package com.example.timecapsule.data.adapter

import com.example.timecapsule.domain.model.OriginalPhotoFormat
import com.example.timecapsule.domain.result.OperationResult
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
 * Replaceable adapter boundary for UID-scoped local app-private storage and DataStore markers.
 */
interface LocalRetentionAdapter {
    /**
     * Persists verified recipient unlocked content with an authoritative server timestamp.
     *
     * @param uid Authenticated recipient UID.
     * @param capsuleId Target capsule identifier.
     * @param verifiedUnlockedAt Authoritative server unlock timestamp.
     * @param text Message text to retain, or null if text is absent.
     * @param photoBytes Photo binary bytes to retain, or null if photo is absent.
     * @param photoFormat Photo format, or null if photo is absent.
     * @return Success if saved durably; Invalid or Unavailable on error.
     */
    suspend fun saveRetainedContent(
        uid: String,
        capsuleId: String,
        verifiedUnlockedAt: Instant,
        text: String?,
        photoBytes: ByteArray?,
        photoFormat: OriginalPhotoFormat?
    ): OperationResult<Unit>

    /**
     * Reads verified retained content for the active account if marker and content files are complete.
     *
     * @param uid Active authenticated account UID.
     * @param capsuleId Target capsule identifier.
     * @return Success with [RetainedContent] if verified; Denied or Unavailable if missing or corrupted.
     */
    suspend fun readRetainedContent(uid: String, capsuleId: String): OperationResult<RetainedContent>

    /** Clears account-scoped retained content for [uid]. */
    suspend fun clearAccountData(uid: String)

    /** Clears all retained content across all accounts during process-wide reset. */
    suspend fun clearAllData()
}
