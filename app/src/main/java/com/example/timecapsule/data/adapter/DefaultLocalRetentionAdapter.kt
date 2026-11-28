package com.example.timecapsule.data.adapter

import com.example.timecapsule.data.media.AndroidOriginalImageProbe
import com.example.timecapsule.data.session.StorageSession
import com.example.timecapsule.domain.model.LockMode
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
import org.jetbrains.annotations.TestOnly

/**
 * Production implementation of [LocalRetentionAdapter] managing UID-scoped app-private retained content.
 */
class DefaultLocalRetentionAdapter(
    private val root: File,
    private val session: () -> SessionSnapshot
) : LocalRetentionAdapter {

    @set:TestOnly
    var faultInjector: (() -> Unit)? = null

    companion object {
        private val lock = StorageSession.lock
        private const val MARKER_VERSION = "3"
        private const val MARKER_FILE_NAME = "verified-marker.properties"
        private const val TEXT_FILE_NAME = "content.text"
        private const val PHOTO_FILE_NAME = "content.photo"
    }

    override suspend fun saveVerifiedUnlockMarker(
        targetSession: SessionSnapshot,
        capsuleId: String,
        verifiedUnlockedAt: Instant,
        title: String?,
        lockMode: LockMode?,
        senderUid: String?,
        recipientUid: String?,
        senderDisplayName: String?,
        recipientDisplayName: String?,
        createdAt: Instant?,
        unlockAt: Instant?,
        creationZone: String?,
        hasText: Boolean,
        hasPhoto: Boolean
    ): OperationResult<Unit> = withContext(Dispatchers.IO) {
        val cleanUid = (targetSession.uid ?: "").trim()
        val cleanCapsuleId = capsuleId.trim()
        if (cleanUid.isBlank() || cleanCapsuleId.isBlank()) {
            return@withContext OperationResult.Invalid("UID and capsule ID must not be blank for unlock marker.")
        }
        if (!cleanCapsuleId.matches(Regex("[A-Za-z0-9_-]+"))) {
            return@withContext OperationResult.Invalid("Invalid capsule ID format for storage.")
        }

        synchronized(lock) {
            if (!targetSession.isAuthenticated || session() != targetSession) {
                return@synchronized OperationResult.Denied("Unlock marker save denied for stale or unauthenticated session generation.")
            }
            try {
                val directory = targetCapsuleDirectory(cleanUid, cleanCapsuleId)
                if (!directory.isDirectory && !directory.mkdirs()) {
                    return@synchronized OperationResult.Unavailable("Cannot create retained content directory.")
                }

                val markerFile = File(directory, MARKER_FILE_NAME)
                val existingProperties = Properties()
                if (markerFile.exists()) {
                    try { markerFile.inputStream().use { existingProperties.load(it) } } catch (_: Exception) {}
                }

                val existingCommitted = existingProperties.getProperty("contentCommitted") == "true"
                val finalHasText = if (existingCommitted) {
                    existingProperties.getProperty("hasText")?.toBooleanStrictOrNull() ?: hasText
                } else {
                    hasText
                }
                val finalHasPhoto = if (existingCommitted) {
                    existingProperties.getProperty("hasPhoto")?.toBooleanStrictOrNull() ?: hasPhoto
                } else {
                    hasPhoto
                }

                val properties = Properties().apply {
                    setProperty("version", MARKER_VERSION)
                    setProperty("uid", cleanUid)
                    setProperty("capsuleId", cleanCapsuleId)
                    setProperty("verifiedUnlockedAt", verifiedUnlockedAt.toString())
                    setProperty("contentCommitted", if (existingCommitted) "true" else "false")
                    setProperty("hasText", finalHasText.toString())
                    setProperty("hasPhoto", finalHasPhoto.toString())
                    existingProperties.getProperty("photoSha256")?.let { setProperty("photoSha256", it) }
                    existingProperties.getProperty("photoFormat")?.let { setProperty("photoFormat", it) }
                    lockMode?.let { setProperty("lockMode", it.name) }
                        ?: existingProperties.getProperty("lockMode")?.let { setProperty("lockMode", it) }
                    title?.takeIf { it.isNotBlank() }?.let { setProperty("title", it) }
                        ?: existingProperties.getProperty("title")?.let { setProperty("title", it) }
                    senderUid?.takeIf { it.isNotBlank() }?.let { setProperty("senderUid", it) }
                        ?: existingProperties.getProperty("senderUid")?.let { setProperty("senderUid", it) }
                    recipientUid?.takeIf { it.isNotBlank() }?.let { setProperty("recipientUid", it) }
                        ?: existingProperties.getProperty("recipientUid")?.let { setProperty("recipientUid", it) }
                    senderDisplayName?.takeIf { it.isNotBlank() }?.let { setProperty("senderDisplayName", it) }
                        ?: existingProperties.getProperty("senderDisplayName")?.let { setProperty("senderDisplayName", it) }
                    recipientDisplayName?.takeIf { it.isNotBlank() }?.let { setProperty("recipientDisplayName", it) }
                        ?: existingProperties.getProperty("recipientDisplayName")?.let { setProperty("recipientDisplayName", it) }
                    createdAt?.let { setProperty("createdAt", it.toString()) }
                        ?: existingProperties.getProperty("createdAt")?.let { setProperty("createdAt", it) }
                    unlockAt?.let { setProperty("unlockAt", it.toString()) }
                        ?: existingProperties.getProperty("unlockAt")?.let { setProperty("unlockAt", it) }
                    creationZone?.takeIf { it.isNotBlank() }?.let { setProperty("creationZone", it) }
                        ?: existingProperties.getProperty("creationZone")?.let { setProperty("creationZone", it) }
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
                OperationResult.Unavailable("Failed to persist unlock marker: ${error.message}")
            }
        }
    }

    override suspend fun saveRetainedContent(
        targetSession: SessionSnapshot,
        capsuleId: String,
        verifiedUnlockedAt: Instant,
        text: String?,
        photoBytes: ByteArray?,
        photoFormat: OriginalPhotoFormat?,
        title: String?,
        lockMode: LockMode?,
        senderDisplayName: String?,
        recipientDisplayName: String?,
        createdAt: Instant?,
        unlockAt: Instant?,
        creationZone: String?
    ): OperationResult<Unit> = withContext(Dispatchers.IO) {
        val cleanUid = (targetSession.uid ?: "").trim()
        val cleanCapsuleId = capsuleId.trim()
        if (cleanUid.isBlank() || cleanCapsuleId.isBlank()) {
            return@withContext OperationResult.Invalid("UID and capsule ID must not be blank for retention.")
        }
        if (!cleanCapsuleId.matches(Regex("[A-Za-z0-9_-]+"))) {
            return@withContext OperationResult.Invalid("Invalid capsule ID format for storage.")
        }
        val retainedText = text?.takeIf { it.isNotBlank() }
        if (retainedText == null && photoBytes == null) {
            return@withContext OperationResult.Invalid("Retained content must contain text, photo, or both.")
        }
        if ((photoBytes == null) != (photoFormat == null) ||
            (photoBytes != null && AndroidOriginalImageProbe.inspect(photoBytes) != photoFormat)) {
            return@withContext OperationResult.Invalid("Retained photo bytes must decode in the declared format.")
        }

        synchronized(lock) {
            if (!targetSession.isAuthenticated || session() != targetSession) {
                return@synchronized OperationResult.Denied("Retained content save denied for stale or unauthenticated session generation.")
            }
            try {
                val directory = targetCapsuleDirectory(cleanUid, cleanCapsuleId)
                if (!directory.isDirectory && !directory.mkdirs()) {
                    return@synchronized OperationResult.Unavailable("Cannot create retained content directory.")
                }

                val markerFile = File(directory, MARKER_FILE_NAME)
                val existingProperties = Properties()
                if (markerFile.exists()) {
                    try { markerFile.inputStream().use { existingProperties.load(it) } } catch (_: Exception) {}
                }

                // 1. Invalidate complete-content authorization BEFORE writing new content files
                val tempProperties = Properties().apply {
                    putAll(existingProperties)
                    setProperty("version", MARKER_VERSION)
                    setProperty("uid", cleanUid)
                    setProperty("capsuleId", cleanCapsuleId)
                    setProperty("verifiedUnlockedAt", verifiedUnlockedAt.toString())
                    setProperty("contentCommitted", "false")
                    setProperty("hasText", "false")
                    setProperty("hasPhoto", "false")
                }
                val preMarker = File.createTempFile("marker-pre-", ".partial", directory)
                try {
                    preMarker.outputStream().use { output ->
                        tempProperties.store(output, null)
                        output.fd.sync()
                    }
                    Files.move(
                        preMarker.toPath(),
                        markerFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                    )
                } finally {
                    preMarker.delete()
                }

                // Fault injection seam for testing interrupted replacements
                faultInjector?.invoke()

                // 2. Write content files atomically
                val textFile = File(directory, TEXT_FILE_NAME)
                if (retainedText == null) Files.deleteIfExists(textFile.toPath())
                else writeAtomic(textFile, retainedText.toByteArray(Charsets.UTF_8))
                val photoFile = File(directory, PHOTO_FILE_NAME)
                if (photoBytes == null) Files.deleteIfExists(photoFile.toPath())
                else writeAtomic(photoFile, photoBytes)

                // 3. Publish complete-content authorization only AFTER files are written
                val properties = Properties().apply {
                    setProperty("version", MARKER_VERSION)
                    setProperty("uid", cleanUid)
                    setProperty("capsuleId", cleanCapsuleId)
                    setProperty("verifiedUnlockedAt", verifiedUnlockedAt.toString())
                    setProperty("contentCommitted", "true")
                    setProperty("hasText", (retainedText != null).toString())
                    setProperty("hasPhoto", (photoBytes != null).toString())
                    photoBytes?.let { setProperty("photoSha256", sha256(it)) }
                    photoFormat?.let { setProperty("photoFormat", it.name) }
                    lockMode?.let { setProperty("lockMode", it.name) }
                        ?: existingProperties.getProperty("lockMode")?.let { setProperty("lockMode", it) }
                    title?.takeIf { it.isNotBlank() }?.let { setProperty("title", it) }
                        ?: existingProperties.getProperty("title")?.let { setProperty("title", it) }
                    senderDisplayName?.takeIf { it.isNotBlank() }?.let { setProperty("senderDisplayName", it) }
                        ?: existingProperties.getProperty("senderDisplayName")?.let { setProperty("senderDisplayName", it) }
                    recipientDisplayName?.takeIf { it.isNotBlank() }?.let { setProperty("recipientDisplayName", it) }
                        ?: existingProperties.getProperty("recipientDisplayName")?.let { setProperty("recipientDisplayName", it) }
                    createdAt?.let { setProperty("createdAt", it.toString()) }
                        ?: existingProperties.getProperty("createdAt")?.let { setProperty("createdAt", it) }
                    unlockAt?.let { setProperty("unlockAt", it.toString()) }
                        ?: existingProperties.getProperty("unlockAt")?.let { setProperty("unlockAt", it) }
                    creationZone?.takeIf { it.isNotBlank() }?.let { setProperty("creationZone", it) }
                        ?: existingProperties.getProperty("creationZone")?.let { setProperty("creationZone", it) }
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
        targetSession: SessionSnapshot,
        capsuleId: String
    ): OperationResult<RetainedContent> = withContext(Dispatchers.IO) {
        val cleanUid = (targetSession.uid ?: "").trim()
        val cleanCapsuleId = capsuleId.trim()
        if (cleanUid.isBlank() || cleanCapsuleId.isBlank()) {
            return@withContext OperationResult.Denied("Authenticated UID and capsule ID are required.")
        }

        synchronized(lock) {
            if (!targetSession.isAuthenticated || session() != targetSession) {
                return@synchronized OperationResult.Denied("Retained content read denied for stale or unauthenticated session generation.")
            }
            readRetainedContentInternal(cleanUid, cleanCapsuleId)
        }
    }

    override suspend fun listVerifiedRecipientEntries(
        targetSession: SessionSnapshot
    ): OperationResult<List<RetainedSummary>> = withContext(Dispatchers.IO) {
        val cleanUid = (targetSession.uid ?: "").trim()
        if (cleanUid.isBlank()) {
            return@withContext OperationResult.Denied("Authenticated UID is required.")
        }

        synchronized(lock) {
            if (!targetSession.isAuthenticated || session() != targetSession) {
                return@synchronized OperationResult.Denied("Retained content read denied for stale or unauthenticated session generation.")
            }
            try {
                val accountDir = accountDirectory(cleanUid)
                if (!accountDir.exists() || !accountDir.isDirectory) {
                    return@synchronized OperationResult.Success(emptyList())
                }

                val capsuleDirs = accountDir.listFiles { file -> file.isDirectory } ?: emptyArray()
                val summaries = mutableListOf<RetainedSummary>()

                for (capsuleDir in capsuleDirs) {
                    val capsuleId = capsuleDir.name
                    if (!capsuleId.matches(Regex("[A-Za-z0-9_-]+"))) continue
                    val summaryRes = readRetainedSummaryInternal(cleanUid, capsuleId)
                    if (summaryRes is OperationResult.Success) {
                        summaries.add(summaryRes.data)
                    }
                }

                val sorted = summaries.sortedWith(
                    compareByDescending<RetainedSummary> { it.verifiedUnlockedAt }
                        .thenBy { it.capsuleId }
                )
                OperationResult.Success(sorted)
            } catch (error: Exception) {
                OperationResult.Unavailable("Failed to list retained content: ${error.message}")
            }
        }
    }

    /** Reads and fully validates retained content and bytes inside synchronized lock. */
    private fun readRetainedContentInternal(
        cleanUid: String,
        cleanCapsuleId: String
    ): OperationResult<RetainedContent> {
        try {
            val directory = targetCapsuleDirectory(cleanUid, cleanCapsuleId)
            val markerFile = File(directory, MARKER_FILE_NAME)
            if (!markerFile.exists()) {
                return OperationResult.Denied("No verified retention marker exists for $cleanCapsuleId.")
            }

            val properties = Properties().apply {
                markerFile.inputStream().use { load(it) }
            }

            val version = properties.getProperty("version")
            val markerUid = properties.getProperty("uid")
            val markerCapsuleId = properties.getProperty("capsuleId")
            val unlockTimeStr = properties.getProperty("verifiedUnlockedAt")

            if ((version != "3" && version != "2" && version != "1") || markerUid != cleanUid || markerCapsuleId != cleanCapsuleId || unlockTimeStr == null) {
                return OperationResult.Denied("Retained marker is invalid or belongs to another account.")
            }

            val verifiedUnlockedAt = try {
                Instant.parse(unlockTimeStr)
            } catch (_: Exception) {
                return OperationResult.Denied("Retained marker timestamp is corrupted.")
            }

            val contentCommittedStr = properties.getProperty("contentCommitted")
            val isCommitted = when {
                contentCommittedStr != null -> contentCommittedStr.toBooleanStrictOrNull() == true
                else -> version == "1" || version == "2" // Legacy v1/v2 records
            }
            if (!isCommitted) {
                return OperationResult.Denied("Retained content is uncommitted or unavailable locally.")
            }

            val hasTextStr = properties.getProperty("hasText")
            val hasPhotoStr = properties.getProperty("hasPhoto")
            val hasText = hasTextStr?.toBooleanStrictOrNull()
                ?: return OperationResult.Denied("Retained marker hasText flag is missing or invalid.")
            val hasPhoto = hasPhotoStr?.toBooleanStrictOrNull()
                ?: return OperationResult.Denied("Retained marker hasPhoto flag is missing or invalid.")

            if (!hasText && !hasPhoto) {
                return OperationResult.Denied("Retained marker declares neither text nor photo content.")
            }

            val textContent = if (hasText) {
                val textFile = File(directory, TEXT_FILE_NAME)
                if (!textFile.exists()) {
                    return OperationResult.Unavailable("Retained text file is missing.")
                }
                val t = textFile.readText(Charsets.UTF_8)
                if (t.isBlank()) {
                    return OperationResult.Unavailable("Retained text content is empty.")
                }
                t
            } else null

            val photoFormat = if (hasPhoto) {
                val formatStr = properties.getProperty("photoFormat") ?: return OperationResult.Unavailable("Retained photo format metadata missing.")
                try {
                    OriginalPhotoFormat.valueOf(formatStr)
                } catch (_: Exception) {
                    return OperationResult.Unavailable("Invalid retained photo format.")
                }
            } else null

            val photoBytes = if (hasPhoto) {
                val photoFile = File(directory, PHOTO_FILE_NAME)
                if (!photoFile.isFile || !OriginalPhotoLimits.acceptsSize(photoFile.length())) {
                    return OperationResult.Unavailable("Retained photo file is missing.")
                }
                val bytes = photoFile.readBytes()
                if (properties.getProperty("photoSha256") != sha256(bytes) ||
                    AndroidOriginalImageProbe.inspect(bytes) != photoFormat) {
                    return OperationResult.Unavailable("Retained photo integrity could not be verified.")
                }
                bytes
            } else null

            return OperationResult.Success(
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
            return OperationResult.Unavailable("Failed to read retained content: ${error.message}")
        }
    }

    /**
     * Reads lightweight retention marker metadata and checks file existence on disk WITHOUT loading or decoding bytes.
     */
    private fun readRetainedSummaryInternal(
        cleanUid: String,
        cleanCapsuleId: String
    ): OperationResult<RetainedSummary> {
        try {
            val directory = targetCapsuleDirectory(cleanUid, cleanCapsuleId)
            val markerFile = File(directory, MARKER_FILE_NAME)
            if (!markerFile.exists()) {
                return OperationResult.Denied("No verified retention marker exists for $cleanCapsuleId.")
            }

            val properties = Properties().apply {
                markerFile.inputStream().use { load(it) }
            }

            val version = properties.getProperty("version")
            val markerUid = properties.getProperty("uid")
            val markerCapsuleId = properties.getProperty("capsuleId")
            val unlockTimeStr = properties.getProperty("verifiedUnlockedAt")

            if ((version != "3" && version != "2" && version != "1") || markerUid != cleanUid || markerCapsuleId != cleanCapsuleId || unlockTimeStr == null) {
                return OperationResult.Denied("Retained marker is invalid or belongs to another account.")
            }

            val verifiedUnlockedAt = try {
                Instant.parse(unlockTimeStr)
            } catch (_: Exception) {
                return OperationResult.Denied("Retained marker timestamp is corrupted.")
            }

            val hasText = properties.getProperty("hasText")?.toBooleanStrictOrNull() ?: false
            val hasPhoto = properties.getProperty("hasPhoto")?.toBooleanStrictOrNull() ?: false
            val contentCommittedStr = properties.getProperty("contentCommitted")
            val isCommitted = when {
                contentCommittedStr != null -> contentCommittedStr.toBooleanStrictOrNull() == true
                else -> version == "1" || (version == "2" && (hasText || hasPhoto))
            }

            val senderUid = properties.getProperty("senderUid")?.takeIf { it.isNotBlank() }
            val recipientUid = properties.getProperty("recipientUid")?.takeIf { it.isNotBlank() }

            val lockModeStr = properties.getProperty("lockMode")
            val lockMode = lockModeStr?.let {
                try { LockMode.valueOf(it) } catch (_: Exception) { null }
            }

            val title = properties.getProperty("title")?.takeIf { it.isNotBlank() }
            val senderDisplayName = properties.getProperty("senderDisplayName")?.takeIf { it.isNotBlank() }
            val recipientDisplayName = properties.getProperty("recipientDisplayName")?.takeIf { it.isNotBlank() }
            val createdAt = properties.getProperty("createdAt")?.let {
                try { Instant.parse(it) } catch (_: Exception) { null }
            }
            val unlockAt = properties.getProperty("unlockAt")?.let {
                try { Instant.parse(it) } catch (_: Exception) { null }
            }
            val creationZone = properties.getProperty("creationZone")?.takeIf { it.isNotBlank() }

            // Check if text and photo files exist on disk WITHOUT reading bytes
            val textFile = File(directory, TEXT_FILE_NAME)
            val photoFile = File(directory, PHOTO_FILE_NAME)

            val textOk = !hasText || (textFile.exists() && textFile.length() > 0)
            val photoOk = !hasPhoto || (photoFile.isFile && OriginalPhotoLimits.acceptsSize(photoFile.length()))

            return OperationResult.Success(
                RetainedSummary(
                    uid = cleanUid,
                    capsuleId = cleanCapsuleId,
                    verifiedUnlockedAt = verifiedUnlockedAt,
                    senderUid = senderUid,
                    recipientUid = recipientUid,
                    lockMode = lockMode,
                    unlockAt = unlockAt,
                    title = title,
                    senderDisplayName = senderDisplayName,
                    recipientDisplayName = recipientDisplayName,
                    createdAt = createdAt,
                    creationZone = creationZone,
                    hasText = hasText,
                    hasPhoto = hasPhoto,
                    isContentAvailable = isCommitted && (hasText || hasPhoto) && textOk && photoOk
                )
            )
        } catch (error: Exception) {
            return OperationResult.Unavailable("Failed to read retained summary: ${error.message}")
        }
    }

    override suspend fun clearAccountData(uid: String): Unit = withContext(Dispatchers.IO) {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) return@withContext
        synchronized(lock) {
            val accountDir = accountDirectory(cleanUid)
            if (accountDir.exists()) {
                if (!accountDir.deleteRecursively() || accountDir.exists()) {
                    throw IOException("Failed to delete retained content directory for $cleanUid")
                }
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
        return File(root, sha256(uid.toByteArray(Charsets.UTF_8)))
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
