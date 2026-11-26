package com.example.timecapsule.data.media

import com.example.timecapsule.data.session.StorageSession
import com.example.timecapsule.domain.model.FrozenPhotoUpload
import com.example.timecapsule.domain.model.PhotoUploadAcknowledgment
import com.example.timecapsule.domain.model.RetainedOriginalPhoto
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Explicit publication handoff from #14; uncertainty never permits deletion. */
enum class PhotoPublicationOutcome { CONFIRMED_PUBLISHED, DEFINITELY_ABANDONED, UNCERTAIN }

/** Authenticated backend operations, replaceable by deterministic JVM fixtures. */
interface PhotoUploadBackend {
    /** Uploads exact bytes and verifies the server acknowledgment; does not publish Firestore records. */
    suspend fun upload(file: File, frozen: FrozenPhotoUpload): PhotoUploadAcknowledgment
    /** Returns a server-sourced publication check; offline/cache absence is insufficient. */
    suspend fun isPublished(capsuleId: String): Boolean
    /** Removes only a server-authorized, unreferenced object; missing objects are already clean. */
    suspend fun deleteUnpublished(path: String)
}

/**
 * Persists immutable upload originals and serializes freeze/upload/cleanup attempts.
 * #14 must durably record its complete operation before calling upload and supply truthful outcomes.
 * This component never creates a capsule or returns SENT.
 */
class OriginalPhotoUploads(
    private val store: OriginalPhotoStore,
    private val journals: File,
    private val backend: PhotoUploadBackend,
    private val session: () -> SessionSnapshot
) {
    companion object {
        // Shared across Activity recreation so an old in-flight attempt cannot race new cleanup.
        private val attempts = Mutex()
    }

    /** Makes a separate immutable copy and durable handoff before the first upload attempt. */
    suspend fun freeze(photo: RetainedOriginalPhoto, captured: SessionSnapshot = session()): OperationResult<FrozenPhotoUpload> =
        withContext(Dispatchers.IO) {
            attempts.withLock {
                if (session() != captured || captured.uid != photo.ownerUid) return@withLock denied()
                var createdCopy: RetainedOriginalPhoto? = null
                var savedDurably = false
                try {
                    val journal = journal(photo.capsuleId)
                    val oldRecord = try {
                        journal.load(photo.ownerUid)
                    } catch (error: Exception) {
                        // Fail closed: Do NOT treat an unreadable existing journal as null/absent.
                        return@withLock failure(error)
                    }
                    val old = oldRecord?.photo
                    if (old != null) {
                        if (old.sha256 != photo.sha256 || old.sizeBytes != photo.sizeBytes) {
                            return@withLock OperationResult.Invalid("This operation already has a frozen photo.")
                        }
                        store.verify(old)
                        return@withLock OperationResult.Success(frozen(old))
                    }
                    val original = store.verify(photo)
                    val copy = store.retain(StorageSession(captured, session), photo.ownerUid, photo.capsuleId, photo.format.mimeType,
                        original.inputStream())
                    createdCopy = copy
                    if (session() != captured) {
                        store.file(copy).delete()
                        return@withLock denied()
                    }
                    journal.save(StorageSession(captured, session), photo.ownerUid, PhotoDraftRecord(photo.capsuleId, "", "", copy))
                    savedDurably = true
                    OperationResult.Success(frozen(copy))
                } catch (cancelled: CancellationException) {
                    // On cancellation or ambiguous state, inspect authoritative journal before deleting.
                    if (createdCopy != null && !savedDurably) {
                        try {
                            val verifiedRecord = journal(photo.capsuleId).load(photo.ownerUid)?.photo
                            if (verifiedRecord != createdCopy) {
                                store.file(createdCopy).delete()
                            }
                        } catch (_: Exception) {
                            // Status is uncertain due to journal read error; preserve copy rather than deleting a potentially valid record.
                        }
                    }
                    throw cancelled
                } catch (error: Exception) {
                    if (createdCopy != null && !savedDurably) {
                        try {
                            val verifiedRecord = journal(photo.capsuleId).load(photo.ownerUid)?.photo
                            if (verifiedRecord != createdCopy) {
                                store.file(createdCopy).delete()
                            }
                        } catch (_: Exception) {
                            // Status is uncertain due to journal read error; preserve copy rather than deleting a potentially valid record.
                        }
                    }
                    failure(error)
                }
            }
        }

    /** Uses the durable frozen copy on every retry and rejects stale-session acknowledgments. */
    suspend fun upload(input: FrozenPhotoUpload, captured: SessionSnapshot = session()): OperationResult<PhotoUploadAcknowledgment> =
        withContext(Dispatchers.IO) {
            attempts.withLock {
                if (session() != captured || captured.uid != input.original.ownerUid) return@withLock denied()
                try {
                    checkFrozen(input)
                    val file = store.verify(input.original)
                    val ack = backend.upload(file, input)
                    if (session() != captured) return@withLock denied()
                    if (ack.objectPath != input.objectPath || ack.sizeBytes != input.original.sizeBytes ||
                        ack.sha256 != input.original.sha256) {
                        return@withLock OperationResult.Invalid("Upload acknowledgment does not match the original.")
                    }
                    OperationResult.Success(ack)
                } catch (cancelled: CancellationException) {
                    // Remote bytes may exist after interruption. Keep the original for identical retry.
                    throw cancelled
                } catch (error: Exception) {
                    failure(error)
                }
            }
        }

    /** Performs foreground cleanup only after #14 resolves publication and its outstanding writes. */
    suspend fun cleanup(input: FrozenPhotoUpload, outcome: PhotoPublicationOutcome, captured: SessionSnapshot = session()): OperationResult<Unit> =
        withContext(Dispatchers.IO) {
            attempts.withLock {
                if (session() != captured || captured.uid != input.original.ownerUid) return@withLock denied()
                if (outcome == PhotoPublicationOutcome.UNCERTAIN) {
                    return@withLock OperationResult.Unavailable("Keep photo until publication is resolved.")
                }
                try {
                    checkFrozen(input)
                    if (outcome == PhotoPublicationOutcome.CONFIRMED_PUBLISHED &&
                        !backend.isPublished(input.original.capsuleId)) {
                        return@withLock OperationResult.Unavailable("Publication is not server-confirmed.")
                    }
                    if (outcome == PhotoPublicationOutcome.DEFINITELY_ABANDONED) {
                        // Rules check absence at deletion time; a denied Firestore get is never absence.
                        backend.deleteUnpublished(input.objectPath)
                    }
                    if (session() != captured) return@withLock denied()
                    synchronized(StorageSession.lock) {
                        StorageSession(captured, session).requireOwner(input.original.ownerUid)
                        val file = store.file(input.original)
                        if (file.exists() && !file.delete()) throw IOException("Local photo cleanup failed.")
                    }
                    OperationResult.Success(Unit)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    failure(error)
                }
            }
        }

    /** Rejects path substitution and upload requests without a matching persisted frozen record. */
    private fun checkFrozen(input: FrozenPhotoUpload) {
        require(input == frozen(input.original)) { "Incorrect photo object path." }
        val saved = journal(input.original.capsuleId).load(input.original.ownerUid)?.photo
        require(saved == input.original) { "Frozen photo is missing or changed." }
    }

    /** Constructs the single approved object path; identities cannot inject path segments. */
    private fun frozen(photo: RetainedOriginalPhoto): FrozenPhotoUpload {
        require(photo.ownerUid.isNotBlank() && !photo.ownerUid.contains('/'))
        require(photo.capsuleId.matches(Regex("[A-Za-z0-9_-]+")))
        return FrozenPhotoUpload(photo, "capsuleMedia/${photo.ownerUid}/${photo.capsuleId}/original")
    }

    /** Isolates each capsule's frozen record from its editable draft journal. */
    private fun journal(id: String): PhotoDraftJournal {
        require(id.matches(Regex("[A-Za-z0-9_-]+")))
        return PhotoDraftJournal(File(journals, id))
    }

    /** Returns a safe account error without revealing another account's photo information. */
    private fun denied(): OperationResult.Denied = OperationResult.Denied("Photo session changed. Sign in again.")

    /** Keeps invalid bytes, denied access and recoverable backend failures distinct. */
    private fun failure(error: Exception): OperationResult<Nothing> = when (error) {
        is InvalidOriginalPhotoException, is IllegalArgumentException ->
            OperationResult.Invalid(error.message ?: "Invalid photo.")
        is SecurityException -> OperationResult.Denied("Photo access denied. Sign in and retry.")
        else -> OperationResult.Unavailable("Photo operation failed. Check connection/storage and retry.")
    }
}
