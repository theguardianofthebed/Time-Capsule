package com.example.timecapsule.data.publication

import com.example.timecapsule.data.media.OriginalPhotoStore
import com.example.timecapsule.data.media.PhotoDraftJournal
import com.example.timecapsule.data.media.PhotoDraftRecord
import com.example.timecapsule.domain.model.CapsuleDraft
import com.example.timecapsule.domain.model.CapsuleLock
import com.example.timecapsule.domain.model.FrozenCapsuleOperation
import com.example.timecapsule.domain.model.FrozenOperationRecord
import com.example.timecapsule.domain.model.LocationTarget
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.domain.model.OriginalPhotoFormat
import com.example.timecapsule.domain.model.PublicationPhase
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Real private journals exercise restart recovery without requiring a Firebase server. */
class PublicationDraftRecoveryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val now = Instant.parse("2026-11-21T00:00:00Z")

    /** Builds an immutable location-locked operation with editable content. */
    private fun operation() = FrozenCapsuleOperation.freeze(
        CapsuleDraft("old", "recipient", "private text", null,
            CapsuleLock(LockMode.LOCATION, location = LocationTarget(37.0, 127.0)),
            isRecipientConfirmed = true), "sender", "UTC", now)

    /** Supplies deterministic authoritative evidence and rejects accidental automatic writes. */
    private fun backend(evidence: PublicationInspection) = object : CapsulePublicationBackend {
        override suspend fun publish(operation: FrozenCapsuleOperation) {
            error("Recovery must not publish implicitly.")
        }
        override suspend fun inspect(operation: FrozenCapsuleOperation) = evidence
    }

    /** Uses a deterministic new identity to expose duplicate replacement attempts. */
    private fun coordinator(journal: FrozenOperationJournal, store: PublicationDraftStore,
        evidence: PublicationInspection = PublicationInspection.ABSENT) =
        CapsulePublicationCoordinator(journal, backend(evidence), null,
            { SessionSnapshot("sender", 1) }, { now }, { "UTC" },
            drafts = store, newId = { "replacement" })

    /** Shares the same private journal layout used by Android, with a replaceable image probe. */
    private fun store(drafts: PhotoDraftJournal) = PrivatePublicationDrafts(
        OriginalPhotoStore(temporary.newFolder()) { OriginalPhotoFormat.JPEG }, drafts)

    /** Confirmed completion removes raw compose content and exposes no frozen text to the UI. */
    @Test fun sentRecoveryClearsOnlyCompletedComposeBuffer() = runBlocking {
        val journal = FrozenOperationJournal(temporary.newFolder())
        val drafts = PhotoDraftJournal(temporary.newFolder())
        drafts.save("sender", PhotoDraftRecord("old", "", "private text", null))
        journal.save(FrozenOperationRecord(operation(), PublicationPhase.SENT))
        val result = coordinator(journal, store(drafts)).recoverPublications()
            as OperationResult.Success
        assertNull(result.data.single().snapshot)
        assertTrue(result.data.single().clearedEditableDraft)
        assertEquals("", drafts.load("sender")?.text)
        assertNotEquals("old", drafts.load("sender")?.capsuleId)
    }

    /** Cleaning an earlier send preserves a newer independent editable draft. */
    @Test fun sentRecoveryPreservesDifferentDraft() = runBlocking {
        val drafts = PhotoDraftJournal(temporary.newFolder())
        drafts.save("sender", PhotoDraftRecord("newer", "", "new message", null))
        assertFalse(store(drafts).clearConfirmed(operation()))
        assertEquals("new message", drafts.load("sender")?.text)
    }

    /** Definite failure is replaced exactly once and recipient confirmation is reset. */
    @Test fun failedOperationGetsStableNewIdentity() = runBlocking {
        val journal = FrozenOperationJournal(temporary.newFolder())
        val drafts = PhotoDraftJournal(temporary.newFolder())
        drafts.save("sender", PhotoDraftRecord("old", "", "private text", null))
        journal.save(FrozenOperationRecord(operation(), PublicationPhase.FAILED))
        val service = coordinator(journal, store(drafts))
        val first = service.abandonForNewDraft("old") as OperationResult.Success
        assertEquals("replacement", first.data.capsuleId)
        assertFalse(first.data.isRecipientConfirmed)
        drafts.save("sender", PhotoDraftRecord("replacement", "", "later edits", null))
        val second = service.abandonForNewDraft("old") as OperationResult.Success
        assertEquals("later edits", second.data.text)
        assertTrue(journal.load("sender", "old")!!.replacementPrepared)
        assertTrue(service.publish(CapsuleDraft("old", "recipient", "private text", null,
            operation().lock, isRecipientConfirmed = true)) is OperationResult.Invalid)
    }

    /** Server absence cannot authorize abandoning an earlier uncertain write. */
    @Test fun unknownOperationCannotBeAbandoned() = runBlocking {
        val journal = FrozenOperationJournal(temporary.newFolder())
        journal.save(FrozenOperationRecord(operation(), PublicationPhase.OUTCOME_UNKNOWN,
            hasUnresolvedWrite = true))
        val result = coordinator(journal, store(PhotoDraftJournal(temporary.newFolder())))
            .abandonForNewDraft("old")
        assertTrue(result is OperationResult.OutcomeUnknown)
        assertNull(journal.load("sender", "old")?.replacementDraftId)
    }

    /** Even an old FAILED marker cannot override unavailable server evidence. */
    @Test fun unavailableServerBlocksAbandonment() = runBlocking {
        val journal = FrozenOperationJournal(temporary.newFolder())
        journal.save(FrozenOperationRecord(operation(), PublicationPhase.FAILED))
        val result = coordinator(journal, store(PhotoDraftJournal(temporary.newFolder())),
            PublicationInspection.UNAVAILABLE).abandonForNewDraft("old")
        assertTrue(result is OperationResult.OutcomeUnknown)
        assertEquals(PublicationPhase.FAILED, journal.load("sender", "old")?.phase)
    }

    /** A crash after recording the replacement identity resumes that exact identity on restart. */
    @Test fun interruptedReplacementResumesWithoutNewId() = runBlocking {
        val journal = FrozenOperationJournal(temporary.newFolder())
        val drafts = PhotoDraftJournal(temporary.newFolder())
        journal.save(FrozenOperationRecord(operation(), PublicationPhase.ABANDONED,
            replacementDraftId = "already-selected"))
        val result = coordinator(journal, store(drafts)).recoverPublications()
        assertTrue(result is OperationResult.Success)
        assertEquals("already-selected", drafts.load("sender")?.capsuleId)
        assertTrue(journal.load("sender", "old")!!.replacementPrepared)
    }

    /** Repeated startup does not resurrect an old replacement over a later draft. */
    @Test fun finishedReplacementDoesNotOverwriteLaterWork() = runBlocking {
        val journal = FrozenOperationJournal(temporary.newFolder())
        val drafts = PhotoDraftJournal(temporary.newFolder())
        journal.save(FrozenOperationRecord(operation(), PublicationPhase.ABANDONED,
            replacementDraftId = "replacement", replacementPrepared = true))
        drafts.save("sender", PhotoDraftRecord("newer", "", "keep this", null))
        assertTrue(coordinator(journal, store(drafts)).recoverPublications() is OperationResult.Success)
        assertEquals("keep this", drafts.load("sender")?.text)
    }

    /** Replacement refuses to overwrite another draft instead of silently losing content. */
    @Test fun replacementPreservesUnrelatedDraft() = runBlocking {
        val drafts = PhotoDraftJournal(temporary.newFolder())
        drafts.save("sender", PhotoDraftRecord("newer", "", "keep this", null))
        try {
            store(drafts).replacement(operation(), "replacement")
            fail("An unrelated draft must remain intact.")
        } catch (expected: IOException) {
            assertEquals("keep this", drafts.load("sender")?.text)
        }
    }

    /** Recovery lists only the authenticated owner's records, even within one shared journal root. */
    @Test fun recoveryDoesNotExposeAnotherAccount() = runBlocking {
        val journal = FrozenOperationJournal(temporary.newFolder())
        journal.save(FrozenOperationRecord(operation(), PublicationPhase.FAILED))
        val service = CapsulePublicationCoordinator(journal, backend(PublicationInspection.ABSENT),
            null, { SessionSnapshot("another", 1) })
        val result = service.recoverPublications() as OperationResult.Success
        assertTrue(result.data.isEmpty())
        assertEquals(PublicationPhase.FAILED, journal.load("sender", "old")?.phase)
    }

    /** A replacement copies exact retained bytes under its new identity without reopening a URI. */
    @Test fun replacementRetainsOriginalBytesWithNewCapsuleOwner() = runBlocking {
        val originals = OriginalPhotoStore(temporary.newFolder()) { OriginalPhotoFormat.JPEG }
        val bytes = byteArrayOf(1, 2, 3, 4)
        val photo = originals.retain("sender", "old", "image/jpeg", bytes.inputStream())
        val draft = CapsuleDraft("old", "recipient", "", null, operation().lock,
            isRecipientConfirmed = true, originalPhoto = photo)
        val frozen = FrozenCapsuleOperation.freeze(draft, "sender", "UTC", now)
        val drafts = PhotoDraftJournal(temporary.newFolder())
        drafts.save("sender", PhotoDraftRecord("old", "", "", photo))
        val replacement = PrivatePublicationDrafts(originals, drafts).replacement(frozen, "new")
        val copied = replacement.originalPhoto!!
        assertEquals("new", copied.capsuleId)
        assertEquals(photo.sha256, copied.sha256)
        assertNotEquals(photo.privateFileName, copied.privateFileName)
        assertTrue(bytes.contentEquals(originals.verify(copied).readBytes()))
        assertTrue(originals.verify(photo).exists())
    }
}
