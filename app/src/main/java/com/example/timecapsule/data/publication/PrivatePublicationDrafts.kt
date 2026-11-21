package com.example.timecapsule.data.publication

import com.example.timecapsule.data.media.OriginalPhotoStore
import com.example.timecapsule.data.media.PhotoDraftJournal
import com.example.timecapsule.data.media.PhotoDraftRecord
import com.example.timecapsule.domain.model.CapsuleDraft
import com.example.timecapsule.domain.model.FrozenCapsuleOperation
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Local handoff for safe replacement drafts and clearing completed compose buffers. */
interface PublicationDraftStore {
    /** Copies a definitely abandoned send to one new editable identity, requiring recipient confirmation. */
    suspend fun replacement(operation: FrozenCapsuleOperation, newId: String): CapsuleDraft
    /** Clears only this completed capsule's editable buffer, leaving independent drafts intact. */
    suspend fun clearConfirmed(operation: FrozenCapsuleOperation): Boolean
}

/** Uses non-backed-up originals and the existing private draft journal; no provider URI is reopened. */
class PrivatePublicationDrafts(
    private val originals: OriginalPhotoStore,
    private val drafts: PhotoDraftJournal
) : PublicationDraftStore {
    /** Persists a replacement before releasing any previous editable photo reference. */
    override suspend fun replacement(operation: FrozenCapsuleOperation, newId: String): CapsuleDraft =
        withContext(Dispatchers.IO) {
            require(newId != operation.capsuleId && newId.matches(Regex("[A-Za-z0-9_-]+")))
            val current = drafts.load(operation.senderUid)
            if (current != null && current.capsuleId != operation.capsuleId && current.capsuleId != newId) {
                throw IOException("A different editable draft already exists.")
            }
            if (current?.capsuleId == newId) return@withContext asDraft(operation, current)
            val copy = operation.photo?.let { original ->
                originals.retain(operation.senderUid, newId, original.format.mimeType,
                    originals.verify(original).inputStream()).also {
                    check(it.sha256 == original.sha256) { "Replacement photo changed." }
                }
            }
            val candidate = PhotoDraftRecord(newId, operation.title ?: "", operation.text, copy)
            val saved = try {
                drafts.replaceIfCurrent(operation.senderUid, operation.capsuleId, candidate)
            } catch (error: Exception) {
                copy?.let { originals.file(it).delete() }
                throw error
            }
            if (copy != null && saved.photo != copy) originals.file(copy).delete()
            // A frozen upload copy may still be needed for remote cleanup; only the distinct
            // editable reference can be released here after the new journal is durable.
            current?.photo?.takeIf { it != operation.photo && it != saved.photo }?.let {
                originals.file(it).delete()
            }
            asDraft(operation, saved)
        }

    /** Removes raw text/photo from the old compose buffer only after confirmed publication. */
    override suspend fun clearConfirmed(operation: FrozenCapsuleOperation): Boolean =
        withContext(Dispatchers.IO) {
            val cleared = drafts.clearIfCurrent(operation.senderUid, operation.capsuleId)
                ?: return@withContext false
            cleared.photo?.takeIf { it != operation.photo }?.let { originals.file(it).delete() }
            true
        }

    /** Maps the saved replacement to an editable draft; confirmation is intentionally reset. */
    private fun asDraft(operation: FrozenCapsuleOperation, saved: PhotoDraftRecord): CapsuleDraft {
        saved.photo?.let { originals.verify(it) }
        return CapsuleDraft(saved.capsuleId, operation.recipientUid, saved.text,
            saved.photo?.let { originals.file(it).toURI().toString() }, operation.lock,
            title = saved.title.ifBlank { null }, isRecipientConfirmed = false,
            originalPhoto = saved.photo)
    }
}
