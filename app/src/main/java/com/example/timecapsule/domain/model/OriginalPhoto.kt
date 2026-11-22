package com.example.timecapsule.domain.model

/** Supported original image formats; the decoded format must match the declared MIME type. */
enum class OriginalPhotoFormat(val mimeType: String) {
    JPEG("image/jpeg"),
    PNG("image/png")
}

/** Shared byte limits for one original photo, counted from the actual input stream. */
object OriginalPhotoLimits {
    const val MIN_BYTES: Long = 1L
    const val MAX_BYTES: Long = 5_242_880L

    /** Includes exactly five MiB and rejects empty or oversized input. */
    fun acceptsSize(actualBytes: Long): Boolean = actualBytes in MIN_BYTES..MAX_BYTES
}

/**
 * Validated original retained in UID-scoped app-private storage for Issue #13.
 * [privateFileName] is a relative file name, never a provider URI or public URL.
 * [sha256] fingerprints the unchanged bytes; [sizeBytes] is their measured stream size.
 * Construct this only after copying and checking the actual image, not from picker metadata.
 * This record alone does not prove remote upload or capsule publication.
 */
data class RetainedOriginalPhoto(
    val ownerUid: String,
    val capsuleId: String,
    val privateFileName: String,
    val format: OriginalPhotoFormat,
    val sizeBytes: Long,
    val sha256: String
)

/**
 * A frozen upload input tying the retained bytes to one stable remote object path.
 * Issue #14 must persist its operation before uploading and await the upload acknowledgment.
 * Retries must reuse this input rather than selecting or transforming another image.
 */
data class FrozenPhotoUpload(
    val original: RetainedOriginalPhoto,
    val objectPath: String
)

/** Server-acknowledged upload identity; this is not a SENT/publication result. */
data class PhotoUploadAcknowledgment(
    val objectPath: String,
    val sizeBytes: Long,
    val sha256: String
)
