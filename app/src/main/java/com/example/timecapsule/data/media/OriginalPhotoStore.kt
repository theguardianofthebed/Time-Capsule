package com.example.timecapsule.data.media

import com.example.timecapsule.domain.model.OriginalPhotoFormat
import com.example.timecapsule.domain.model.OriginalPhotoLimits
import com.example.timecapsule.domain.model.RetainedOriginalPhoto
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** Validates a retained candidate without decoding an unbounded full-resolution bitmap. */
fun interface OriginalImageProbe {
    /** Returns the decoded format, or null when the candidate is unreadable or unsafe. */
    fun inspect(file: File): OriginalPhotoFormat?
}

/** Expected invalid input; permission and I/O failures remain distinct exceptions for the caller. */
class InvalidOriginalPhotoException(message: String) : IOException(message)

/**
 * Copies one original into non-backed-up app-private storage without trusting provider sizes.
 * Call from an I/O dispatcher. A unique candidate leaves any prior selection intact on failure.
 * This store retains bytes only; operation persistence, upload and cleanup are separate steps.
 */
class OriginalPhotoStore(
    private val root: File,
    private val probe: OriginalImageProbe
) {
    /**
     * Consumes and closes [input], enforces the byte cap while streaming and preserves exact bytes.
     * The caller owns [input] if argument validation fails before this method starts consuming it.
     * Invalid images are rejected; permission, storage and cancellation failures are propagated.
     */
    suspend fun retain(
        ownerUid: String,
        capsuleId: String,
        declaredMimeType: String?,
        input: InputStream
    ): RetainedOriginalPhoto {
        require(ownerUid.isNotBlank() && capsuleId.isNotBlank())
        val format = OriginalPhotoFormat.entries.firstOrNull { it.mimeType == declaredMimeType }
        val directory = File(File(root, digestName(ownerUid)), digestName(capsuleId))
        var candidate: File? = null
        var retained = false
        try {
            // Always close the provider stream, including unsupported-format and disk failures.
            val result = input.use { source ->
                if (format == null) throw InvalidOriginalPhotoException("Choose a JPEG or PNG photo.")
                if (!directory.isDirectory && !directory.mkdirs()) {
                    throw IOException("Cannot create private photo storage.")
                }
                val file = File(directory, "${UUID.randomUUID()}.original")
                candidate = file
                val digest = MessageDigest.getInstance("SHA-256")
                var count = 0L
                file.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = source.read(buffer)
                        if (read == -1) break
                        if (read == 0) continue
                        count += read
                        if (count > OriginalPhotoLimits.MAX_BYTES) {
                            throw InvalidOriginalPhotoException("Photo must be at most 5 MiB.")
                        }
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                    }
                    output.fd.sync()
                }
                coroutineContext.ensureActive()
                if (!OriginalPhotoLimits.acceptsSize(count)) {
                    throw InvalidOriginalPhotoException("The selected photo is empty.")
                }
                if (probe.inspect(file) != format) {
                    throw InvalidOriginalPhotoException("Photo is unreadable or its format does not match.")
                }
                coroutineContext.ensureActive()
                RetainedOriginalPhoto(
                    ownerUid, capsuleId, file.name, format, count, hex(digest.digest())
                )
            }
            retained = true
            return result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            // Only this attempt's incomplete candidate is removed; older originals are untouched.
            if (!retained) candidate?.delete()
        }
    }

    /** Hashes identity components so provider/account text cannot escape private directories. */
    private fun digestName(value: String): String =
        hex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))

    /** Encodes a digest as its stable lowercase hexadecimal fingerprint. */
    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
