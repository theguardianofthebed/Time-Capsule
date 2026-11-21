package com.example.timecapsule.data.publication

import com.example.timecapsule.domain.model.CapsuleLock
import com.example.timecapsule.domain.model.FrozenCapsuleOperation
import com.example.timecapsule.domain.model.FrozenOperationRecord
import com.example.timecapsule.domain.model.LocationTarget
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.domain.model.OriginalPhotoFormat
import com.example.timecapsule.domain.model.PublicationPhase
import com.example.timecapsule.domain.model.RetainedOriginalPhoto
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.util.Properties

/**
 * Durable UID-scoped submission records. Supply a directory under Android noBackupFilesDir.
 * Call on an I/O dispatcher. Every write completes and syncs before upload/publication may start.
 * A process-wide lock also serializes independent instances during Activity recreation.
 */
class FrozenOperationJournal(private val root: File) {
    /** Identifies the private store when coordinating attempts across recreated instances. */
    val storageIdentity: String
        get() = root.canonicalPath

    companion object {
        private val access = Any()
    }

    /**
     * Creates a frozen record or updates its progress without changing the logical capsule.
     * A different snapshot for an existing ID is rejected, including after FAILED or SENT.
     * Phase changes must be authorized by the publication coordinator's actual network results.
     */
    fun save(record: FrozenOperationRecord) = synchronized(access) {
        val operation = record.operation
        operation.verify()
        val previous = load(operation.senderUid, operation.capsuleId)
        require(previous == null ||
            previous.operation.canonicalPayload() == operation.canonicalPayload()) {
            "This capsule ID already belongs to different frozen content."
        }
        val destination = target(operation.senderUid, operation.capsuleId)
        val directory = destination.parentFile ?: throw IOException("Invalid journal directory.")
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Cannot create private operation storage.")
        }
        val temporary = File.createTempFile("operation-", ".partial", directory)
        try {
            temporary.outputStream().use { stream ->
                encode(record).store(stream, null)
                stream.fd.sync()
            }
            Files.move(temporary.toPath(), destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }

    /** Reads and validates one identity; corrupted data is an error, never an absent operation. */
    fun load(uid: String, capsuleId: String): FrozenOperationRecord? = synchronized(access) {
        val source = target(uid, capsuleId)
        if (!source.exists()) return@synchronized null
        try {
            val fields = Properties().apply { source.inputStream().use { load(it) } }
            require(fields.required("version") == "1") { "Unsupported operation format." }
            require(fields.required("senderUid") == uid)
            require(fields.required("capsuleId") == capsuleId)
            val photo = fields.getProperty("photoFile")?.let { name ->
                RetainedOriginalPhoto(uid, capsuleId, name,
                    OriginalPhotoFormat.valueOf(fields.required("photoFormat")),
                    fields.required("photoSize").toLong(), fields.required("photoHash"))
            }
            val location = fields.getProperty("latitude")?.let {
                LocationTarget(it.toDouble(), fields.required("longitude").toDouble(),
                    fields.required("radius").toDouble())
            }
            val operation = FrozenCapsuleOperation(
                capsuleId, uid, fields.required("recipientUid"), fields.getProperty("title"),
                fields.required("text"), CapsuleLock(LockMode.valueOf(fields.required("lockMode")),
                    fields.getProperty("unlockAt")?.let(Instant::parse), location),
                fields.required("creationZone"), photo, fields.required("snapshotDigest")
            )
            operation.verify()
            FrozenOperationRecord(operation, PublicationPhase.valueOf(fields.required("phase")))
        } catch (error: RuntimeException) {
            throw IOException("Saved capsule operation is invalid; do not start a new send.", error)
        }
    }

    /** Lists only the current UID's durable operations for foreground recovery. */
    fun list(uid: String): List<FrozenOperationRecord> = synchronized(access) {
        val directory = accountDirectory(uid)
        if (!directory.exists()) return@synchronized emptyList()
        val files = directory.listFiles() ?: throw IOException("Cannot read operation storage.")
        files.filter { it.isFile && it.extension == "properties" }.sortedBy { it.name }.map {
            load(uid, it.nameWithoutExtension)
                ?: throw IOException("Operation disappeared during recovery.")
        }
    }

    /** Encodes explicit versioned fields; provider URIs and server-generated timestamps are excluded. */
    private fun encode(record: FrozenOperationRecord): Properties = Properties().apply {
        val operation = record.operation
        setProperty("version", "1")
        setProperty("capsuleId", operation.capsuleId)
        setProperty("senderUid", operation.senderUid)
        setProperty("recipientUid", operation.recipientUid)
        operation.title?.let { setProperty("title", it) }
        setProperty("text", operation.text)
        setProperty("creationZone", operation.creationZone)
        setProperty("snapshotDigest", operation.snapshotDigest)
        setProperty("phase", record.phase.name)
        setProperty("lockMode", operation.lock.mode.name)
        operation.lock.unlockAt?.let { setProperty("unlockAt", it.toString()) }
        operation.lock.location?.let {
            setProperty("latitude", it.latitude.toString())
            setProperty("longitude", it.longitude.toString())
            setProperty("radius", it.radiusMeters.toString())
        }
        operation.photo?.let {
            setProperty("photoFile", it.privateFileName)
            setProperty("photoFormat", it.format.name)
            setProperty("photoSize", it.sizeBytes.toString())
            setProperty("photoHash", it.sha256)
        }
    }

    /** Rejects partial required records instead of guessing values during recovery. */
    private fun Properties.required(key: String): String =
        getProperty(key) ?: throw IOException("Missing operation field: $key")

    /** Restricts operation names to one path component. */
    private fun target(uid: String, capsuleId: String): File {
        require(capsuleId.matches(Regex("[A-Za-z0-9_-]+"))) { "Invalid capsule ID." }
        return File(accountDirectory(uid), "$capsuleId.properties")
    }

    /** Hashes the account key so unrelated UIDs cannot select another account's records. */
    private fun accountDirectory(uid: String): File {
        require(uid.isNotBlank()) { "An authenticated account is required." }
        val key = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(root, key)
    }
}
