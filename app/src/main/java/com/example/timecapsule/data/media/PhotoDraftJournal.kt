package com.example.timecapsule.data.media

import com.example.timecapsule.domain.model.OriginalPhotoFormat
import com.example.timecapsule.domain.model.RetainedOriginalPhoto
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID

/** Private draft fields needed to resume the same original after process death. */
data class PhotoDraftRecord(
    val capsuleId: String,
    val title: String,
    val text: String,
    val photo: RetainedOriginalPhoto?
)

/** Atomically replaces one UID-scoped draft record; callers serialize access and use I/O threads. */
class PhotoDraftJournal(private val root: File) {
    companion object {
        private val access = Any()
    }

    /** Persists a draft before old original bytes are eligible for local cleanup. */
    fun save(uid: String, draft: PhotoDraftRecord): Unit = synchronized(access) {
        require(uid.isNotBlank())
        require(draft.photo == null || (draft.photo.ownerUid == uid &&
            draft.photo.capsuleId == draft.capsuleId))
        if (!root.isDirectory && !root.mkdirs()) throw IOException("Cannot create draft storage.")
        val properties = Properties().apply {
            setProperty("capsuleId", draft.capsuleId)
            setProperty("title", draft.title)
            setProperty("text", draft.text)
            draft.photo?.let {
                setProperty("file", it.privateFileName)
                setProperty("format", it.format.name)
                setProperty("size", it.sizeBytes.toString())
                setProperty("hash", it.sha256)
            }
        }
        val temporary = File.createTempFile("draft-", ".partial", root)
        try {
            temporary.outputStream().use { output ->
                properties.store(output, null)
                output.fd.sync()
            }
            Files.move(temporary.toPath(), target(uid).toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }

    /** Loads only the requested UID's fields; malformed records fail visibly instead of disappearing. */
    fun load(uid: String): PhotoDraftRecord? = synchronized(access) {
        val file = target(uid)
        if (!file.exists()) return@synchronized null
        val properties = Properties().apply { file.inputStream().use { load(it) } }
        val id = properties.getProperty("capsuleId") ?: throw IOException("Draft is incomplete.")
        val name = properties.getProperty("file")
        val photo = if (name == null) null else try {
            RetainedOriginalPhoto(uid, id, name,
                OriginalPhotoFormat.valueOf(properties.getProperty("format")),
                properties.getProperty("size").toLong(), properties.getProperty("hash"))
        } catch (error: RuntimeException) {
            throw IOException("Saved photo information is invalid.", error)
        }
        PhotoDraftRecord(id, properties.getProperty("title", ""),
            properties.getProperty("text", ""), photo)
    }

    /** Replaces only the expected old draft; an already-created replacement keeps all later edits. */
    fun replaceIfCurrent(
        uid: String,
        expectedId: String,
        replacement: PhotoDraftRecord
    ): PhotoDraftRecord = synchronized(access) {
        val current = load(uid)
        if (current?.capsuleId == replacement.capsuleId) return@synchronized current
        if (current != null && current.capsuleId != expectedId) {
            throw IOException("A different editable draft already exists.")
        }
        save(uid, replacement)
        replacement
    }

    /** Clears a completed compose buffer without removing a newer independent draft. */
    fun clearIfCurrent(uid: String, capsuleId: String): PhotoDraftRecord? = synchronized(access) {
        val current = load(uid) ?: return@synchronized null
        if (current.capsuleId != capsuleId) return@synchronized null
        save(uid, PhotoDraftRecord(UUID.randomUUID().toString(), "", "", null))
        current
    }

    /** Hashes the UID to isolate journals without putting account identifiers in file names. */
    private fun target(uid: String): File {
        val key = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(root, "$key.properties")
    }
}
