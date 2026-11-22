package com.example.timecapsule.data.media

import com.example.timecapsule.data.AccountSessionManager
import com.example.timecapsule.data.adapter.AuthAdapter
import com.example.timecapsule.data.adapter.FirestoreAdapter
import com.example.timecapsule.data.session.DefaultSessionTracker
import com.example.timecapsule.domain.model.FrozenPhotoUpload
import com.example.timecapsule.domain.model.OriginalPhotoFormat
import com.example.timecapsule.domain.model.PhotoUploadAcknowledgment
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.model.RetainedOriginalPhoto
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest

/** Exercises real private files/journals with a deterministic backend, not Firebase integration. */
class OriginalPhotoUploadsTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var store: OriginalPhotoStore
    private lateinit var journals: File
    private lateinit var service: OriginalPhotoUploads
    private lateinit var backend: Backend
    private var session = SessionSnapshot("sender", 1)

    /** Creates independent files, session and backend for each scenario. */
    @Before fun setup() {
        store = OriginalPhotoStore(temporary.newFolder()) { OriginalPhotoFormat.PNG }
        journals = temporary.newFolder()
        backend = Backend()
        service = OriginalPhotoUploads(store, journals, backend) { session }
    }

    /** Copies a small fixture; image decoding itself belongs to Android device checks. */
    private suspend fun selected(): RetainedOriginalPhoto =
        store.retain("sender", "capsule-1", "image/png", byteArrayOf(1, 2, 3).inputStream())

    /** Unwraps the successful freeze and fails immediately if preparation did not succeed. */
    private suspend fun freeze(): FrozenPhotoUpload =
        (service.freeze(selected()) as OperationResult.Success).data

    /** Restarted services reuse the same persisted frozen bytes even if the editable copy is removed. */
    @Test fun frozenOriginalSurvivesRestart() = runBlocking {
        val original = selected()
        val frozen = (service.freeze(original) as OperationResult.Success).data
        store.file(original).delete()
        val restarted = OriginalPhotoUploads(store, journals, backend) { session }
        assertTrue(restarted.upload(frozen) is OperationResult.Success)
        assertArrayEquals(byteArrayOf(1, 2, 3), backend.uploaded)
    }

    /** A failed upload keeps the same path/hash and bytes available for retry. */
    @Test fun failedUploadRetriesSameOriginal() = runBlocking {
        val frozen = freeze()
        backend.failUpload = true
        assertTrue(service.upload(frozen) is OperationResult.Unavailable)
        backend.failUpload = false
        assertTrue(service.upload(frozen) is OperationResult.Success)
        assertEquals(listOf(frozen.objectPath, frozen.objectPath), backend.paths)
        assertArrayEquals(byteArrayOf(1, 2, 3), backend.uploaded)
    }

    /** Upload acknowledgment mismatch cannot become a successful usable-photo handoff. */
    @Test fun wrongAcknowledgmentIsRejected() = runBlocking {
        val frozen = freeze()
        backend.wrongAck = true
        assertTrue(service.upload(frozen) is OperationResult.Invalid)
    }

    /** A substituted path is rejected before any remote upload occurs. */
    @Test fun wrongObjectPathDoesNotUpload() = runBlocking {
        val frozen = freeze()
        assertTrue(service.upload(frozen.copy(objectPath = "other/path")) is OperationResult.Invalid)
        assertTrue(backend.paths.isEmpty())
    }

    /** Byte mutation, including equal-length mutation, is detected from SHA-256. */
    @Test fun changedFrozenBytesDoNotUpload() = runBlocking {
        val frozen = freeze()
        store.file(frozen.original).writeBytes(byteArrayOf(3, 2, 1))
        assertTrue(service.upload(frozen) is OperationResult.Invalid)
        assertTrue(backend.paths.isEmpty())
    }

    /** Missing originals cannot produce an acknowledgment. */
    @Test fun missingFrozenFileDoesNotUpload() = runBlocking {
        val frozen = freeze()
        store.file(frozen.original).delete()
        assertTrue(service.upload(frozen) is OperationResult.Invalid)
    }

    /** A new photo cannot replace a frozen image for the same operation ID. */
    @Test fun frozenIdentityCannotBeReplaced() = runBlocking {
        freeze()
        val replacement = store.retain("sender", "capsule-1", "image/png", byteArrayOf(4).inputStream())
        assertTrue(service.freeze(replacement) is OperationResult.Invalid)
    }

    /** Sign-out or another session generation prevents a stale upload success. */
    @Test fun sessionChangeRejectsAcknowledgment() = runBlocking {
        val frozen = freeze()
        backend.afterUpload = { session = SessionSnapshot("sender", 3) }
        assertTrue(service.upload(frozen) is OperationResult.Denied)
        assertTrue(store.file(frozen.original).exists())
    }

    /** An unrelated account cannot retry another account's frozen original. */
    @Test fun unrelatedAccountCannotUpload() = runBlocking {
        val frozen = freeze()
        session = SessionSnapshot("other", 2)
        assertTrue(service.upload(frozen) is OperationResult.Denied)
        assertTrue(backend.paths.isEmpty())
    }

    /** Unknown publication keeps both remote and local bytes. */
    @Test fun uncertainOutcomeNeverDeletes() = runBlocking {
        val frozen = freeze()
        assertTrue(service.cleanup(frozen, PhotoPublicationOutcome.UNCERTAIN) is OperationResult.Unavailable)
        assertEquals(0, backend.deletes)
        assertTrue(store.file(frozen.original).exists())
    }

    /** Definite abandonment removes the unreferenced remote object before releasing local bytes. */
    @Test fun definiteAbandonmentCleansUp() = runBlocking {
        val frozen = freeze()
        assertTrue(service.cleanup(frozen, PhotoPublicationOutcome.DEFINITELY_ABANDONED) is OperationResult.Success)
        assertEquals(1, backend.deletes)
        assertFalse(store.file(frozen.original).exists())
    }

    /** Backend denial after publication preserves local recovery data. */
    @Test fun publishedRemoteCannotBeDeleted() = runBlocking {
        val frozen = freeze()
        backend.published = true
        assertTrue(service.cleanup(frozen, PhotoPublicationOutcome.DEFINITELY_ABANDONED) is OperationResult.Denied)
        assertTrue(store.file(frozen.original).exists())
    }

    /** Confirmed publication releases only local upload bytes, never remote content. */
    @Test fun confirmedPublicationOnlyReleasesLocalCopy() = runBlocking {
        val frozen = freeze()
        backend.published = true
        assertTrue(service.cleanup(frozen, PhotoPublicationOutcome.CONFIRMED_PUBLISHED) is OperationResult.Success)
        assertEquals(0, backend.deletes)
        assertFalse(store.file(frozen.original).exists())
    }

    /** Cancellation is propagated and the frozen original remains available. */
    @Test fun cancelledUploadRetainsRetryCopy() = runBlocking {
        val frozen = freeze()
        backend.cancelUpload = true
        var cancelled = false
        try { service.upload(frozen) } catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
        assertTrue(store.file(frozen.original).exists())
    }

    /** Repeated same-account foreground auth checks preserve session generation and permit upload. */
    @Test fun repeatedForegroundAuthCheckDoesNotRejectUploadAck() = runBlocking {
        val authAdapter = object : AuthAdapter {
            override fun activeUid(): String = "sender"
            override fun activeEmail(): String = "sender@example.com"
            override fun activeDisplayName(): String = "Sender"
            override suspend fun signIn(email: String, password: String) = OperationResult.Success("sender")
            override suspend fun signUp(email: String, password: String, displayName: String) = OperationResult.Success("sender")
            override suspend fun updateDisplayName(displayName: String) = OperationResult.Success(Unit)
            override fun signOut() {}
        }
        val tracker = DefaultSessionTracker()
        val manager = AccountSessionManager(
            authAdapter,
            object : FirestoreAdapter {
                override fun isAvailable(): Boolean = true
                override fun firestoreInstance() = null
                override suspend fun getRecipientProfile(uid: String) = OperationResult.Success(
                    RecipientProfile(uid, "Name")
                )
                override suspend fun saveRecipientProfile(profile: RecipientProfile) = OperationResult.Success(Unit)
            },
            tracker
        )
        manager.restoreSessionIfAuthenticated()
        val serviceWithManager = OriginalPhotoUploads(store, journals, backend) { tracker.currentSession() }
        val original = selected()
        val frozen = (serviceWithManager.freeze(original) as OperationResult.Success).data

        // Simulate repeated foreground auth checks
        manager.restoreSessionIfAuthenticated()
        manager.restoreSessionIfAuthenticated()

        val uploadRes = serviceWithManager.upload(frozen)
        assertTrue("Upload must succeed after repeated same-account checks", uploadRes is OperationResult.Success)
    }

    /** Failure during journal save after creating private copy cleans up the new unreferenced copy. */
    @Test fun failedJournalSaveCleansUpNewlyCreatedCopy() = runBlocking {
        // Create a journals directory containing a file named "capsule-fail" so journal(id) cannot create its directory
        val invalidJournals = temporary.newFolder()
        File(invalidJournals, "capsule-fail").createNewFile()

        val failingService = OriginalPhotoUploads(store, invalidJournals, backend) { session }
        val original = store.retain("sender", "capsule-fail", "image/png", byteArrayOf(10, 20).inputStream())

        val freezeRes = failingService.freeze(original)
        assertTrue("Freeze must return failure when journal save fails", freezeRes is OperationResult.Unavailable)

        // The newly created copy in store must have been deleted, leaving only the original selection
        val origFile = store.file(original)
        assertTrue(origFile.exists())
        val dir = origFile.parentFile
        val remainingFiles = dir?.listFiles()?.filter { it.name != origFile.name } ?: emptyList()
        assertTrue("Newly created frozen copy must be deleted on journal failure", remainingFiles.isEmpty())
    }

    /** Malformed journal file returns typed failure and is NOT overwritten or replaced by freeze. */
    @Test fun malformedJournalReadErrorFailsClosedWithoutOverwriting() = runBlocking {
        val corruptedJournals = temporary.newFolder()
        val journalDir = File(corruptedJournals, "capsule-corrupt")
        journalDir.mkdirs()
        val key = MessageDigest.getInstance("SHA-256").digest("sender".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val targetFile = File(journalDir, "$key.properties")
        // Write invalid corrupted properties content (missing capsuleId property)
        targetFile.writeText("invalid_key_value_no_capsule_id\nformat=JPEG\n")
        val initialContent = targetFile.readText()

        val failingService = OriginalPhotoUploads(store, corruptedJournals, backend) { session }
        val original = store.retain("sender", "capsule-corrupt", "image/png", byteArrayOf(5, 6).inputStream())

        val res = failingService.freeze(original)
        assertTrue("Freeze must fail closed on malformed journal", res is OperationResult.Unavailable)
        assertEquals("Malformed journal file must not be overwritten", initialContent, targetFile.readText())
    }

    /** Backend fixture exposes the exact bytes/path and explicit failure/publication scenarios. */
    private class Backend : PhotoUploadBackend {
        var uploaded = byteArrayOf()
        val paths = mutableListOf<String>()
        var failUpload = false
        var wrongAck = false
        var cancelUpload = false
        var published = false
        var deletes = 0
        var afterUpload: () -> Unit = {}

        /** Records uploaded bytes or injects the selected failure. */
        override suspend fun upload(file: File, frozen: FrozenPhotoUpload): PhotoUploadAcknowledgment {
            paths += frozen.objectPath
            if (cancelUpload) throw CancellationException()
            if (failUpload) throw IOException()
            uploaded = file.readBytes()
            afterUpload()
            return PhotoUploadAcknowledgment(if (wrongAck) "wrong/path" else frozen.objectPath,
                uploaded.size.toLong(), frozen.original.sha256)
        }
        /** Simulates a server-sourced publication result. */
        override suspend fun isPublished(capsuleId: String): Boolean = published
        /** Models backend denial of deleting published media. */
        override suspend fun deleteUnpublished(path: String) {
            if (published) throw SecurityException()
            deletes++
        }
    }
}
