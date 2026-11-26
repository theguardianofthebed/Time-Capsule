package com.example.timecapsule.data.media

import com.example.timecapsule.data.session.StorageSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.timecapsule.domain.model.RetainedOriginalPhoto
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Opens provider content once and retains originals outside Android backup and shared storage. */
class AndroidPhotoInput(context: Context) : PhotoDraftStorage {
    private val context = context.applicationContext
    private val root = File(this.context.noBackupFilesDir, "original-photos")
    private val store = OriginalPhotoStore(root, AndroidOriginalImageProbe())
    private val journal = PhotoDraftJournal(File(this.context.noBackupFilesDir, "photo-drafts"))
    private val journalMutex = Mutex()

    /** Serializes atomic draft writes so older state cannot overwrite a newer save. */
    override suspend fun save(session: StorageSession, uid: String, draft: PhotoDraftRecord) = withContext(Dispatchers.IO) {
        journalMutex.withLock { journal.save(session, uid, draft) }
    }

    /** Restores an account draft and verifies its private original without reopening a provider URI. */
    override suspend fun restore(uid: String): PhotoDraftRecord? = withContext(Dispatchers.IO) {
        journalMutex.withLock {
            journal.load(uid)?.also { record -> record.photo?.let { store.verify(it) } }
        }
    }

    /** Copies and validates provider bytes on I/O; never trusts URI names or reported sizes. */
    override suspend fun retain(session: StorageSession, uid: String, capsuleId: String, uri: String): RetainedOriginalPhoto =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val source = Uri.parse(uri)
            val mime = resolver.getType(source)
            val input = resolver.openInputStream(source) ?: throw FileNotFoundException()
            store.retain(session, uid, capsuleId, mime, input)
        }

    /** Resolves only a safe, identity-scoped original name under this store. */
    override fun file(photo: RetainedOriginalPhoto): File = store.file(photo)

    /** Releases an unsubmitted local selection; frozen upload inputs must not use this method. */
    override suspend fun remove(photo: RetainedOriginalPhoto) = withContext(Dispatchers.IO) {
        val file = store.file(photo)
        if (file.exists() && !file.delete()) throw java.io.IOException("Cannot remove local photo.")
        Unit
    }

    /** Decodes a small preview from private bytes, keeping full-resolution pixels out of the UI. */
    suspend fun preview(photo: RetainedOriginalPhoto): Bitmap? = withContext(Dispatchers.IO) {
        val file = file(photo)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
