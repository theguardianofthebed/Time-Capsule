package com.example.timecapsule.data.publication

import com.example.timecapsule.data.session.fixtureSession
import com.example.timecapsule.domain.model.CapsuleDraft
import com.example.timecapsule.domain.model.CapsuleLock
import com.example.timecapsule.domain.model.FrozenCapsuleOperation
import com.example.timecapsule.domain.model.FrozenOperationRecord
import com.example.timecapsule.domain.model.LocationTarget
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.domain.model.OriginalPhotoFormat
import com.example.timecapsule.domain.model.PublicationPhase
import com.example.timecapsule.domain.model.RetainedOriginalPhoto
import java.io.IOException
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Tests stable content identity and real filesystem recovery without Firebase or an Android device. */
class FrozenOperationJournalTest {
    @get:Rule val temporary = TemporaryFolder()
    private val now = Instant.parse("2026-11-21T00:00:00Z")

    /** Builds a confirmed text draft with a valid future time. */
    private fun draft(id: String = "capsule-one"): CapsuleDraft = CapsuleDraft(
        id, "recipient", "한글\nmessage:🙂", null,
        CapsuleLock(LockMode.TIME, now.plusSeconds(3600)),
        title = "Title", isRecipientConfirmed = true
    )

    /** Freezes deterministic fixtures without relying on the device clock/time zone. */
    private fun freeze(value: CapsuleDraft = draft()): FrozenCapsuleOperation =
        FrozenCapsuleOperation.freeze(value, "sender", "Asia/Seoul", now)

    /** Repeated preparation of the same operation preserves its ID and fingerprint. */
    @Test fun repeatedFreezeKeepsIdentity() {
        val first = freeze()
        val second = freeze()
        assertEquals(first, second)
        assertEquals(64, first.snapshotDigest.length)
    }

    /** Later draft edits cannot mutate the submitted snapshot. */
    @Test fun draftEditsDoNotChangeFrozenContent() {
        val draft = draft()
        val frozen = freeze(draft)
        val edited = draft.copy(text = "Changed")
        assertNotEquals(edited.text, frozen.text)
        assertEquals(freeze(draft).snapshotDigest, frozen.snapshotDigest)
    }

    /** Field boundaries and null/empty titles remain distinct in canonical UTF-8. */
    @Test fun canonicalEncodingHasUnambiguousFields() {
        assertNotEquals(freeze(draft().copy(title = null)).snapshotDigest,
            freeze(draft().copy(title = "")).snapshotDigest)
        assertNotEquals(freeze(draft().copy(title = "a\nb", text = "c")).snapshotDigest,
            freeze(draft().copy(title = "a", text = "b\nc")).snapshotDigest)
    }

    /** Independent deliberate sends remain distinct even when their text and locks match. */
    @Test fun deliberateSecondCapsuleHasNewIdentity() {
        assertNotEquals(freeze().snapshotDigest, freeze(draft("capsule-two")).snapshotDigest)
    }

    /** Title-only submissions do not count as content. */
    @Test fun titleOnlyIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { freeze(draft().copy(text = "  ")) }
    }

    /** Provider URI metadata alone cannot establish valid photo content. */
    @Test fun rawProviderUriIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            freeze(draft().copy(localPhotoUri = "content://photo"))
        }
    }

    /** A send cannot freeze a recipient who has not been explicitly confirmed. */
    @Test fun unconfirmedRecipientIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            freeze(draft().copy(isRecipientConfirmed = false))
        }
    }

    /** Both-lock snapshots preserve complete coordinates and UTC time across journal recreation. */
    @Test fun restartRestoresExactFrozenOperation() {
        val root = temporary.newFolder()
        val operation = freeze(draft().copy(lock = CapsuleLock(LockMode.TIME_AND_LOCATION,
            now.plusSeconds(7200), LocationTarget(37.631, 127.079))))
        val record = FrozenOperationRecord(operation, PublicationPhase.OUTCOME_UNKNOWN)
        FrozenOperationJournal(root).save(fixtureSession("sender"), record)
        assertEquals(record, FrozenOperationJournal(root).load("sender", operation.capsuleId))
    }

    /** Restore verifies the original snapshot without treating a now-past time as changed content. */
    @Test fun existingOperationCanBeRecoveredAfterItsTimePasses() {
        val operation = freeze()
        // No clock participates in verify: an old operation must not get a new identity on retry.
        operation.verify()
        assertThrows(IllegalArgumentException::class.java) {
            FrozenCapsuleOperation.freeze(draft(), "sender", "Asia/Seoul", now.plusSeconds(7200))
        }
    }

    /** A content change cannot replace an existing ID, even with a newly computed valid digest. */
    @Test fun changedContentCannotOverwriteExistingOperation() {
        val journal = FrozenOperationJournal(temporary.newFolder())
        val original = freeze()
        journal.save(fixtureSession("sender"), FrozenOperationRecord(original))
        val changed = freeze(draft().copy(text = "Changed"))
        assertThrows(IllegalArgumentException::class.java) {
            journal.save(fixtureSession("sender"), FrozenOperationRecord(changed))
        }
        assertEquals(original, journal.load("sender", original.capsuleId)?.operation)
    }

    /** Updating progress preserves the immutable payload across a second journal instance. */
    @Test fun progressUpdateDoesNotChangeSnapshot() {
        val root = temporary.newFolder()
        val operation = freeze()
        FrozenOperationJournal(root).save(fixtureSession("sender"), FrozenOperationRecord(operation))
        FrozenOperationJournal(root).save(fixtureSession("sender"), FrozenOperationRecord(operation, PublicationPhase.PUBLISHING))
        assertEquals(FrozenOperationRecord(operation, PublicationPhase.PUBLISHING),
            FrozenOperationJournal(root).load("sender", operation.capsuleId))
    }

    /** Another account cannot load or list this account's retained operation. */
    @Test fun journalIsAccountScoped() {
        val journal = FrozenOperationJournal(temporary.newFolder())
        journal.save(fixtureSession("sender"), FrozenOperationRecord(freeze()))
        assertNull(journal.load("other", "capsule-one"))
        assertTrue(journal.list("other").isEmpty())
        assertEquals(1, journal.list("sender").size)
    }

    /** Missing required fields surface a recovery error instead of looking like an absent send. */
    @Test fun corruptRecordCannotBecomeAbsentOperation() {
        val root = temporary.newFolder()
        val journal = FrozenOperationJournal(root)
        journal.save(fixtureSession("sender"), FrozenOperationRecord(freeze()))
        root.walkTopDown().first { it.extension == "properties" }.writeText("version=1\n")
        assertThrows(IOException::class.java) { journal.load("sender", "capsule-one") }
    }

    /** The digest is checked again when saving, catching an altered in-memory snapshot. */
    @Test fun staleDigestIsRejected() {
        val journal = FrozenOperationJournal(temporary.newFolder())
        assertThrows(IllegalArgumentException::class.java) {
            journal.save(fixtureSession("sender"), FrozenOperationRecord(freeze().copy(text = "Changed")))
        }
    }

    /** Photo-only sends retain original identity; different local copies do not alter server content. */
    @Test fun photoOnlySnapshotAndCopyIdentityAreStable() {
        val photo = RetainedOriginalPhoto("sender", "capsule-one",
            "00000000-0000-0000-0000-000000000000.original", OriginalPhotoFormat.PNG, 20, "a".repeat(64))
        val value = draft().copy(text = "", originalPhoto = photo)
        val original = freeze(value)
        val copy = freeze(value.copy(originalPhoto = photo.copy(
            privateFileName = "11111111-1111-1111-1111-111111111111.original")))
        assertEquals(original.snapshotDigest, copy.snapshotDigest)
        assertEquals("capsuleMedia/sender/capsule-one/original", original.photoObjectPath)
        val root = temporary.newFolder()
        FrozenOperationJournal(root).save(fixtureSession("sender"), FrozenOperationRecord(original))
        assertEquals(original, FrozenOperationJournal(root).load("sender", "capsule-one")?.operation)
    }

    /** A path traversal ID is rejected before creating any account journal file. */
    @Test fun invalidOperationPathIsRejected() {
        val journal = FrozenOperationJournal(temporary.newFolder())
        assertThrows(IllegalArgumentException::class.java) { journal.load("sender", "../other") }
    }
}
