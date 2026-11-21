package com.example.timecapsule.data.media

import com.example.timecapsule.domain.model.OriginalPhotoFormat
import com.example.timecapsule.domain.model.RetainedOriginalPhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Verifies real atomic journal round trips and UID isolation without an Android runtime. */
class PhotoDraftJournalTest {
    @get:Rule val temporary = TemporaryFolder()

    /** A new journal instance restores exact identity, text and original fingerprint. */
    @Test fun restartRestoresPrivateRecord() {
        val root = temporary.newFolder()
        val photo = RetainedOriginalPhoto("a", "capsule", "photo.original", OriginalPhotoFormat.PNG, 3, "hash")
        val record = PhotoDraftRecord("capsule", "Title", "한글\nmessage", photo)
        PhotoDraftJournal(root).save("a", record)
        assertEquals(record, PhotoDraftJournal(root).load("a"))
    }

    /** Account B cannot obtain A's draft, even when IDs contain path separators. */
    @Test fun accountJournalsAreIsolated() {
        val journal = PhotoDraftJournal(temporary.newFolder())
        journal.save("../a", PhotoDraftRecord("capsule", "", "private", null))
        assertNull(journal.load("b"))
        assertEquals("private", journal.load("../a")?.text)
    }

    /** Saving removal replaces the old pointer while preserving the same editable draft. */
    @Test fun removalIsDurable() {
        val journal = PhotoDraftJournal(temporary.newFolder())
        val photo = RetainedOriginalPhoto("a", "capsule", "photo.original", OriginalPhotoFormat.PNG, 3, "hash")
        journal.save("a", PhotoDraftRecord("capsule", "", "", photo))
        journal.save("a", PhotoDraftRecord("capsule", "", "message", null))
        assertNull(journal.load("a")?.photo)
        assertEquals("message", journal.load("a")?.text)
    }
}
