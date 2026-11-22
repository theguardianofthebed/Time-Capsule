package com.example.timecapsule.data.media

import com.example.timecapsule.domain.model.RetainedOriginalPhoto
import java.io.File

/** Replaceable media/persistence boundary for lifecycle tests without Android provider objects. */
interface PhotoDraftStorage {
    /** Returns a validated private original, or throws a specific input/access/I/O failure. */
    suspend fun retain(uid: String, capsuleId: String, uri: String): RetainedOriginalPhoto
    /** Resolves private bytes without consulting the original provider. */
    fun file(photo: RetainedOriginalPhoto): File
    /** Releases only an editable selection, never a frozen upload original. */
    suspend fun remove(photo: RetainedOriginalPhoto)
    /** Atomically saves account-scoped fields. */
    suspend fun save(uid: String, draft: PhotoDraftRecord)
    /** Restores fields and validates retained bytes, or returns null for a new account. */
    suspend fun restore(uid: String): PhotoDraftRecord?
}
