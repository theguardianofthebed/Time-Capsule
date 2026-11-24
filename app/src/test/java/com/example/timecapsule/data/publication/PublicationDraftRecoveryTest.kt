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
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

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

    /** Journal write failure during replacement cleans up unreferenced photo copy without corrupting existing state. */
    @Test fun replacementFailsSafelyOnJournalWriteFailure() = runBlocking {
        val photoRootDir = temporary.newFolder()
        val originals = OriginalPhotoStore(photoRootDir) { OriginalPhotoFormat.JPEG }
        val bytes = byteArrayOf(5, 6, 7, 8)
        val photo = originals.retain("sender", "old", "image/jpeg", bytes.inputStream())
        val draft = CapsuleDraft("old", "recipient", "test text", null, operation().lock,
            isRecipientConfirmed = true, originalPhoto = photo)
        val frozen = FrozenCapsuleOperation.freeze(draft, "sender", "UTC", now)

        fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val replacementDir = File(File(photoRootDir, digest("sender")), digest("new"))
        // Verify replacement directory is empty before replacement attempt
        assertTrue(replacementDir.listFiles().isNullOrEmpty())

        var copyExistedDuringFailure = false
        val badJournalFolder = temporary.newFile("unwritable-file")
        val badJournal = object : PhotoDraftJournal(badJournalFolder) {
            override fun replaceIfCurrent(uid: String, expectedId: String, replacement: PhotoDraftRecord): PhotoDraftRecord {
                // Verify that the replacement photo copy WAS created in replacementDir before journal write fails
                if (replacementDir.listFiles()?.isNotEmpty() == true) {
                    copyExistedDuringFailure = true
                }
                return super.replaceIfCurrent(uid, expectedId, replacement)
            }
        }
        val store = PrivatePublicationDrafts(originals, badJournal)

        try {
            store.replacement(frozen, "new")
            fail("Expected replacement to fail due to unwritable journal.")
        } catch (expected: Exception) {
            // Verify replacement copy was created before failure
            assertTrue("Replacement copy should exist prior to journal failure", copyExistedDuringFailure)
            // Original frozen photo must remain intact
            assertTrue(originals.verify(photo).exists())
            // Replacement photo copy must be cleaned up from replacementDir since journal save failed
            assertTrue(replacementDir.listFiles().isNullOrEmpty())
        }
    }

    /** Interrupted replacement preserves the copied photo if the journal durably saved it before interruption. */
    @Test fun interruptedReplacementPreservesCopiedPhotoIfJournalSavedIt() = runBlocking {
        val originals = OriginalPhotoStore(temporary.newFolder()) { OriginalPhotoFormat.JPEG }
        val bytes = byteArrayOf(9, 10, 11, 12)
        val photo = originals.retain("sender", "old", "image/jpeg", bytes.inputStream())
        val draft = CapsuleDraft("old", "recipient", "text", null, operation().lock,
            isRecipientConfirmed = true, originalPhoto = photo)
        val frozen = FrozenCapsuleOperation.freeze(draft, "sender", "UTC", now)
        val draftRoot = temporary.newFolder()
        val journal = object : PhotoDraftJournal(draftRoot) {
            override fun replaceIfCurrent(uid: String, expectedId: String, replacement: PhotoDraftRecord): PhotoDraftRecord {
                super.replaceIfCurrent(uid, expectedId, replacement)
                // Inject post-commit cancellation after replacement record has been committed to disk
                throw CancellationException("Simulated interruption after commit")
            }
        }
        val store = PrivatePublicationDrafts(originals, journal)

        try {
            store.replacement(frozen, "new")
            fail("Expected cancellation exception")
        } catch (expected: CancellationException) {
            // Verified that post-commit interruption occurred
        }

        // Verify the copied photo file exists and is durably referenced by the journal
        val savedRecord = journal.load("sender")
        assertNotNull(savedRecord)
        assertEquals("new", savedRecord?.capsuleId)
        val copiedPhoto = savedRecord!!.photo!!
        assertTrue(originals.verify(copiedPhoto).exists())
        assertTrue(originals.verify(photo).exists())
    }

    /** Restart recovery uses the existing replacement draft and ID without duplicating photo copies. */
    @Test fun restartRecoveryResumesSameReplacementIdWithoutDuplicateCopy() = runBlocking {
        val originals = OriginalPhotoStore(temporary.newFolder()) { OriginalPhotoFormat.JPEG }
        val bytes = byteArrayOf(13, 14, 15, 16)
        val photo = originals.retain("sender", "old", "image/jpeg", bytes.inputStream())
        val draft = CapsuleDraft("old", "recipient", "text", null, operation().lock,
            isRecipientConfirmed = true, originalPhoto = photo)
        val frozen = FrozenCapsuleOperation.freeze(draft, "sender", "UTC", now)

        val opJournal = FrozenOperationJournal(temporary.newFolder())
        opJournal.save(FrozenOperationRecord(frozen, PublicationPhase.FAILED))

        val draftJournal = PhotoDraftJournal(temporary.newFolder())
        draftJournal.save("sender", PhotoDraftRecord("old", "", "text", photo))

        val store = PrivatePublicationDrafts(originals, draftJournal)
        val coord = CapsulePublicationCoordinator(opJournal, backend(PublicationInspection.ABSENT), null,
            { SessionSnapshot("sender", 1) }, { now }, { "UTC" }, drafts = store, newId = { "new-1" })

        // First call creates replacement "new-1"
        val firstRes = coord.abandonForNewDraft("old") as OperationResult.Success
        assertEquals("new-1", firstRes.data.capsuleId)
        val firstCopiedPhoto = firstRes.data.originalPhoto!!
        assertTrue(originals.verify(firstCopiedPhoto).exists())

        // Simulate process restart: create a new coordinator instance
        val restartedCoord = CapsulePublicationCoordinator(opJournal, backend(PublicationInspection.ABSENT), null,
            { SessionSnapshot("sender", 1) }, { now }, { "UTC" }, drafts = store, newId = { "new-2" })

        val recoveryRes = restartedCoord.recoverPublications()
        assertTrue(recoveryRes is OperationResult.Success)

        val secondRes = restartedCoord.abandonForNewDraft("old") as OperationResult.Success
        assertEquals("new-1", secondRes.data.capsuleId) // Resumes "new-1", not "new-2"
        assertEquals(firstCopiedPhoto, secondRes.data.originalPhoto)
        assertTrue(originals.verify(photo).exists())
        assertTrue(originals.verify(firstCopiedPhoto).exists())
    }

    /** Recovery preserves an unrelated newer draft without overwriting it. */
    @Test fun recoveryPreservesUnrelatedNewerDraft() = runBlocking {
        val originals = OriginalPhotoStore(temporary.newFolder()) { OriginalPhotoFormat.JPEG }
        val bytes = byteArrayOf(17, 18, 19, 20)
        val photo = originals.retain("sender", "unrelated-newer", "image/jpeg", bytes.inputStream())

        val opJournal = FrozenOperationJournal(temporary.newFolder())
        opJournal.save(FrozenOperationRecord(operation(), PublicationPhase.ABANDONED,
            replacementDraftId = "new", replacementPrepared = true))

        val draftJournal = PhotoDraftJournal(temporary.newFolder())
        draftJournal.save("sender", PhotoDraftRecord("unrelated-newer", "Newer Title", "keep this text", photo))

        val store = PrivatePublicationDrafts(originals, draftJournal)
        val coord = CapsulePublicationCoordinator(opJournal, backend(PublicationInspection.ABSENT), null,
            { SessionSnapshot("sender", 1) }, { now }, { "UTC" }, drafts = store)

        val result = coord.recoverPublications()
        assertTrue(result is OperationResult.Success)

        val currentDraft = draftJournal.load("sender")
        assertNotNull(currentDraft)
        assertEquals("unrelated-newer", currentDraft?.capsuleId)
        assertEquals("keep this text", currentDraft?.text)
        assertTrue(originals.verify(photo).exists())
    }

    /** Unreadable operation journal during recovery fails safely without abandoning or corrupting operations. */
    @Test fun journalReadFailureDuringRecoveryFailsClosed() = runBlocking {
        val root = temporary.newFolder()
        val key = MessageDigest.getInstance("SHA-256").digest("sender".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        // Create account directory as a file so directory.listFiles() fails closed
        File(root, key).createNewFile()
        val journal = FrozenOperationJournal(root)
        val service = CapsulePublicationCoordinator(journal, backend(PublicationInspection.ABSENT),
            null, { SessionSnapshot("sender", 1) })

        val result = service.recoverPublications()
        assertTrue(result is OperationResult.Unavailable)
    }

    /** Replacement draft preserves location target across process restart and journal reload. */
    @Test fun replacementDraftPreservesLocationTargetAcrossProcessRestart() = runBlocking {
        val originals = OriginalPhotoStore(temporary.newFolder()) { OriginalPhotoFormat.JPEG }
        val opFolder = temporary.newFolder()
        val draftFolder = temporary.newFolder()

        val locationTarget = LocationTarget(latitude = 37.5665, longitude = 126.9780, radiusMeters = 150.0)
        val locationLock = CapsuleLock(LockMode.LOCATION, location = locationTarget)
        val draft = CapsuleDraft("loc-old", "recipient", "location message", null, locationLock, isRecipientConfirmed = true)
        val frozen = FrozenCapsuleOperation.freeze(draft, "sender", "UTC", now)

        val opJournal = FrozenOperationJournal(opFolder)
        opJournal.save(FrozenOperationRecord(frozen, PublicationPhase.FAILED))

        val draftJournal = PhotoDraftJournal(draftFolder)
        draftJournal.save("sender", PhotoDraftRecord("loc-old", "", "location message", null, locationTarget, "37.5665, 126.9780"))

        val store = PrivatePublicationDrafts(originals, draftJournal)
        val coord = CapsulePublicationCoordinator(opJournal, backend(PublicationInspection.ABSENT), null,
            { SessionSnapshot("sender", 1) }, { now }, { "UTC" }, drafts = store, newId = { "loc-new-1" })

        val replaceRes = coord.abandonForNewDraft("loc-old") as OperationResult.Success
        assertEquals("loc-new-1", replaceRes.data.capsuleId)
        assertNotNull(replaceRes.data.lock.location)
        assertEquals(37.5665, replaceRes.data.lock.location!!.latitude, 0.0001)

        val newDraftJournal = PhotoDraftJournal(draftFolder)
        val restoredFromJournal = newDraftJournal.load("sender")

        assertNotNull(restoredFromJournal)
        assertEquals("loc-new-1", restoredFromJournal!!.capsuleId)
        val target = restoredFromJournal.locationTarget!!
        assertEquals(37.5665, target.latitude, 0.0001)
        assertEquals(126.9780, target.longitude, 0.0001)
        assertEquals(150.0, target.radiusMeters, 0.01)
    }
}
