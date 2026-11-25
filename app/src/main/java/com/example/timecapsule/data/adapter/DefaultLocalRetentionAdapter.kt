package com.example.timecapsule.data.adapter

import com.example.timecapsule.domain.model.OriginalPhotoFormat
import com.example.timecapsule.domain.model.OriginalPhotoLimits
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production implementation of [LocalRetentionAdapter] managing UID-scoped app-private retained content.
 *
 * Security & Data Isolation Architecture:
 * 1. Storage Location: Root directory MUST be under Android `noBackupFilesDir` to prevent cloud auto-backup.
 * 2. Hashed Account Isolation: Stores content under `root/retained-content/<hashed-uid>/<capsule-id>/`
 *    so filenames never contain plain text user UIDs or sensitive user identifiers.
 * 3. Server Timestamp Requirement: Retained markers store [RetainedContent.verifiedUnlockedAt] derived strictly from authoritative
 *    server unlock evidence. Local device clock changes cannot manufacture valid retention markers.
 * 4. Atomic Verification Gate: Offline reads require matching UID, valid marker, and complete, readable content files.
 *    Missing text or photo files cause the read to fail closed ([OperationResult.Unavailable]).
 *
 * @param root Base directory for retained content storage, typically `File(noBackupFilesDir, "retained-content")`.
 * @param session Optional session supplier for active session validation.
 */
class DefaultLocalRetentionAdapter(
    private val root: File,
    private val session: (() -> SessionSnapshot)? = null
) : LocalRetentionAdapter {

    companion object {
        private val lock = Any()
        private const val MARKER_VERSION = "1"
        private const val MARKER_FILE_NAME = "verified-marker.properties"
        private const val TEXT_FILE_NAME = "content.text"
        private const val PHOTO_FILE_NAME = "content.photo"
    }

    override suspend fun saveRetainedContent(
        uid: String,
        capsuleId: String,
        verifiedUnlockedAt: Instant,
        text: String?,
        photoBytes: ByteArray?,
        photoFormat: OriginalPhotoFormat?
    ): OperationResult<Unit> = withContext(Dispatchers.IO) {
        val cleanUid = uid.trim()
        val cleanCapsuleId = capsuleId.trim()
        if (cleanUid.isBlank() || cleanCapsuleId.isBlank()) {
            return@withContext OperationResult.Invalid("UID and capsule ID must not be blank for retention.")
        }
        val activeSession = session?.invoke()
        if (activeSession != null && (!activeSession.isAuthenticated || activeSession.uid != cleanUid)) {
            return@withContext OperationResult.Denied("Retained content save denied for foreign or unauthenticated session.")
        }
        if (!cleanCapsuleId.matches(Regex("[A-Za-z0-9_-]+"))) {
            return@withContext OperationResult.Invalid("Invalid capsule ID format for storage.")
        }
        if (text == null && photoBytes == null) {
            return@withContext OperationResult.Invalid("Retained content must contain text, photo, or both.")
        }
        if (photoBytes != null && photoFormat == null) {
            return@withContext OperationResult.Invalid("Photo format is required when retaining photo bytes.")
        }

        synchronized(lock) {
            try {
                val directory = targetCapsuleDirectory(cleanUid, cleanCapsuleId)
                if (!directory.isDirectory && !directory.mkdirs()) {
                    return@synchronized OperationResult.Unavailable("Cannot create retained content directory.")
                }

                // Write text content if present
                if (text != null) {
                    val textFile = File(directory, TEXT_FILE_NAME)
                    writeAtomic(textFile, text.toByteArray(Charsets.UTF_8))
                }

                // Write photo content if present
                if (photoBytes != null) {
                    val photoFile = File(directory, PHOTO_FILE_NAME)
                    writeAtomic(photoFile, photoBytes)
                }

                // Write verified marker metadata
                val markerFile = File(directory, MARKER_FILE_NAME)
                val properties = Properties().apply {
                    setProperty("version", MARKER_VERSION)
                    setProperty("uid", cleanUid)
                    setProperty("capsuleId", cleanCapsuleId)
                    setProperty("verifiedUnlockedAt", verifiedUnlockedAt.toString())
                    setProperty("hasText", (text != null).toString())
                    setProperty("hasPhoto", (photoBytes != null).toString())
                    photoFormat?.let { setProperty("photoFormat", it.name) }
                }

                val tempMarker = File.createTempFile("marker-", ".partial", directory)
                try {
                    tempMarker.outputStream().use { output ->
                        properties.store(output, null)
                        output.fd.sync()
                    }
                    Files.move(
                        tempMarker.toPath(),
                        markerFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                    )
                } finally {
                    tempMarker.delete()
                }

                OperationResult.Success(Unit)
            } catch (error: Exception) {
                OperationResult.Unavailable("Failed to persist retained content: ${error.message}")
            }
        }
    }

    override suspend fun readRetainedContent(
        uid: String,
        capsuleId: String
    ): OperationResult<RetainedContent> = withContext(Dispatchers.IO) {
        val cleanUid = uid.trim()
        val cleanCapsuleId = capsuleId.trim()
        if (cleanUid.isBlank() || cleanCapsuleId.isBlank()) {
            return@withContext OperationResult.Denied("Authenticated UID and capsule ID are required.")
        }
        val activeSession = session?.invoke()
        if (activeSession != null && (!activeSession.isAuthenticated || activeSession.uid != cleanUid)) {
            return@withContext OperationResult.Denied("Retained content read denied for foreign or unauthenticated session.")
        }

        synchronized(lock) {
            try {
                val directory = targetCapsuleDirectory(cleanUid, cleanCapsuleId)
                val markerFile = File(directory, MARKER_FILE_NAME)
                if (!markerFile.exists()) {
                    return@synchronized OperationResult.Denied("No verified retention marker exists for $cleanCapsuleId.")
                }

                val properties = Properties().apply {
                    markerFile.inputStream().use { load(it) }
                }

                val version = properties.getProperty("version")
                val markerUid = properties.getProperty("uid")
                val markerCapsuleId = properties.getProperty("capsuleId")
                val unlockTimeStr = properties.getProperty("verifiedUnlockedAt")

                if (version != MARKER_VERSION || markerUid != cleanUid || markerCapsuleId != cleanCapsuleId || unlockTimeStr == null) {
                    return@synchronized OperationResult.Denied("Retained marker is invalid or belongs to another account.")
                }

                val verifiedUnlockedAt = try {
                    Instant.parse(unlockTimeStr)
                } catch (_: Exception) {
                    return@synchronized OperationResult.Denied("Retained marker timestamp is corrupted.")
                }

                val hasTextStr = properties.getProperty("hasText")
                val hasPhotoStr = properties.getProperty("hasPhoto")
                val hasText = hasTextStr?.toBooleanStrictOrNull()
                    ?: return@synchronized OperationResult.Denied("Retained marker hasText flag is missing or invalid.")
                val hasPhoto = hasPhotoStr?.toBooleanStrictOrNull()
                    ?: return@synchronized OperationResult.Denied("Retained marker hasPhoto flag is missing or invalid.")

                if (!hasText && !hasPhoto) {
                    return@synchronized OperationResult.Denied("Retained marker declares neither text nor photo content.")
                }

                val textContent = if (hasText) {
                    val textFile = File(directory, TEXT_FILE_NAME)
                    if (!textFile.exists()) {
                        return@synchronized OperationResult.Unavailable("Retained text file is missing.")
                    }
                    val t = textFile.readText(Charsets.UTF_8)
                    if (t.isBlank()) {
                        return@synchronized OperationResult.Unavailable("Retained text content is empty.")
                    }
                    t
                } else null

                val photoFormat = if (hasPhoto) {
                    val formatStr = properties.getProperty("photoFormat") ?: return@synchronized OperationResult.Unavailable("Retained photo format metadata missing.")
                    try {
                        OriginalPhotoFormat.valueOf(formatStr)
                    } catch (_: Exception) {
                        return@synchronized OperationResult.Unavailable("Invalid retained photo format.")
                    }
                } else null

                val photoBytes = if (hasPhoto) {
                    val photoFile = File(directory, PHOTO_FILE_NAME)
                    if (!photoFile.exists()) {
                        return@synchronized OperationResult.Unavailable("Retained photo file is missing.")
                    }
                    val bytes = photoFile.readBytes()
                    if (!OriginalPhotoLimits.acceptsSize(bytes.size.toLong())) {
                        return@synchronized OperationResult.Unavailable("Retained photo size is invalid.")
                    }
                    bytes
                } else null

                OperationResult.Success(
                    RetainedContent(
                        uid = cleanUid,
                        capsuleId = cleanCapsuleId,
                        text = textContent,
                        photoBytes = photoBytes,
                        photoFormat = photoFormat,
                        verifiedUnlockedAt = verifiedUnlockedAt
                    )
                )
            } catch (error: Exception) {
                OperationResult.Unavailable("Failed to read retained content: ${error.message}")
            }
        }
    }

    override suspend fun clearAccountData(uid: String): Unit = withContext(Dispatchers.IO) {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) return@withContext
        synchronized(lock) {
            val accountDir = accountDirectory(cleanUid)
            if (accountDir.exists()) {
                accountDir.deleteRecursively()
            }
        }
    }

    override suspend fun clearAllData(): Unit = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (root.exists()) {
                root.deleteRecursively()
            }
        }
    }

    /** Helper performing atomic file write via temporary file move. */
    private fun writeAtomic(targetFile: File, bytes: ByteArray) {
        val parent = targetFile.parentFile ?: throw IOException("Invalid target directory.")
        val temp = File.createTempFile("retain-", ".partial", parent)
        try {
            temp.outputStream().use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            Files.move(
                temp.toPath(),
                targetFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            temp.delete()
        }
    }

    /** Resolves the capsule-specific directory inside account-hashed storage. */
    private fun targetCapsuleDirectory(uid: String, capsuleId: String): File {
        return File(accountDirectory(uid), capsuleId)
    }

    /** Computes SHA-256 hash of [uid] to prevent account names in file system paths. */
    private fun accountDirectory(uid: String): File {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(root, hash)
    }
}
