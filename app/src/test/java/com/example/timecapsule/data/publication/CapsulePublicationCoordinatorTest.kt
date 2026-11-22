package com.example.timecapsule.data.publication

import com.example.timecapsule.domain.model.CapsuleDraft
import com.example.timecapsule.domain.model.CapsuleLock
import com.example.timecapsule.domain.model.FrozenCapsuleOperation
import com.example.timecapsule.domain.model.FrozenOperationRecord
import com.example.timecapsule.domain.model.FrozenPhotoUpload
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.domain.model.OriginalPhotoFormat
import com.example.timecapsule.domain.model.PhotoUploadAcknowledgment
import com.example.timecapsule.domain.model.PublicationPhase
import com.example.timecapsule.domain.model.RetainedOriginalPhoto
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot
import java.io.IOException
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Uses real local journals and controlled backend/media acknowledgments, not Firebase integration. */
class CapsulePublicationCoordinatorTest {
    @get:Rule val temporary = TemporaryFolder()
    private val now = Instant.parse("2026-11-21T00:00:00Z")
    private lateinit var journal: FrozenOperationJournal
    @Volatile private var session = SessionSnapshot("sender", 1)

    /** Gives every test its own private-store identity and active session. */
    @Before fun setup() { journal = FrozenOperationJournal(temporary.newFolder()) }

    /** Builds a validated confirmed draft with optional original photo. */
    private fun draft(photo: Boolean = false): CapsuleDraft {
        val original = if (photo) RetainedOriginalPhoto("sender", "one",
            "00000000-0000-0000-0000-000000000000.original", OriginalPhotoFormat.PNG,
            3, "a".repeat(64)) else null
        return CapsuleDraft("one", "recipient", "message", null,
            CapsuleLock(LockMode.TIME, now.plusSeconds(3600)),
            isRecipientConfirmed = true, originalPhoto = original)
    }

    /** Injects deterministic clock/session and controlled publication behavior. */
    private fun coordinator(
        backend: CapsulePublicationBackend,
        media: PublicationMedia? = null
    ) = CapsulePublicationCoordinator(journal, backend, media, { session }, { now }, { "UTC" })

    /** A full frozen record exists on disk before the backend receives any call. */
    @Test fun textOnlySavesBeforePublication() = runBlocking {
        val service = coordinator(CapsulePublicationBackend { operation ->
            assertEquals(operation, journal.load("sender", "one")?.operation)
            assertEquals(PublicationPhase.PUBLISHING, journal.load("sender", "one")?.phase)
        })
        assertTrue(service.publish(draft()) is OperationResult.Success)
        assertEquals(PublicationPhase.SENT, journal.load("sender", "one")?.phase)
    }

    /** The backend cannot publish while a required photo upload is awaiting acknowledgment. */
    @Test fun photoAcknowledgmentPrecedesPublication() = runBlocking {
        val uploadStarted = CompletableDeferred<Unit>()
        val acknowledge = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val media = object : Media() {
            /** Holds the photo ACK so the test can inspect the publication boundary. */
            override suspend fun upload(photo: FrozenPhotoUpload): OperationResult<PhotoUploadAcknowledgment> {
                assertEquals(PublicationPhase.UPLOADING, journal.load("sender", "one")?.phase)
                uploadStarted.complete(Unit)
                acknowledge.await()
                return super.upload(photo)
            }
        }
        val service = coordinator(CapsulePublicationBackend { calls.incrementAndGet(); Unit }, media)
        val result = async { service.publish(draft(true)) }
        withTimeout(5000) { uploadStarted.await() }
        assertEquals(0, calls.get())
        assertFalse(result.isCompleted)
        acknowledge.complete(Unit)
        assertTrue(withTimeout(5000) { result.await() } is OperationResult.Success)
        assertEquals(1, calls.get())
    }

    /** Upload failure cannot create either recipient metadata or protected text. */
    @Test fun photoFailurePreventsPublication() = runBlocking {
        val calls = AtomicInteger()
        val media = object : Media() {
            /** Injects a failed required upload. */
            override suspend fun upload(photo: FrozenPhotoUpload) =
                OperationResult.Unavailable("Upload failed")
        }
        val result = coordinator(CapsulePublicationBackend { calls.incrementAndGet(); Unit }, media)
            .publish(draft(true))
        assertTrue(result is OperationResult.Unavailable)
        assertEquals(0, calls.get())
        assertEquals(PublicationPhase.FAILED, journal.load("sender", "one")?.phase)
    }

    /** Wrong-path media acknowledgments cannot authorize publication. */
    @Test fun mismatchedPhotoAckPreventsPublication() = runBlocking {
        val calls = AtomicInteger()
        val media = object : Media() {
            /** Returns an acknowledgment for another remote object. */
            override suspend fun upload(photo: FrozenPhotoUpload) =
                OperationResult.Success(PhotoUploadAcknowledgment("wrong/path", 3, "a".repeat(64)))
        }
        assertTrue(coordinator(CapsulePublicationBackend { calls.incrementAndGet(); Unit }, media)
            .publish(draft(true)) is OperationResult.Invalid)
        assertEquals(0, calls.get())
    }

    /** Concurrent taps join one SDK attempt and cannot claim SENT before its ACK. */
    @Test fun duplicateTapsJoinOnePublication() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val ack = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val service = coordinator(CapsulePublicationBackend {
            calls.incrementAndGet()
            entered.complete(Unit)
            ack.await()
        })
        val first = async { service.publish(draft()) }
        withTimeout(5000) { entered.await() }
        val second = async { service.publish(draft()) }
        assertEquals(PublicationPhase.PUBLISHING, journal.load("sender", "one")?.phase)
        ack.complete(Unit)
        assertTrue(withTimeout(5000) { first.await() } is OperationResult.Success)
        assertTrue(withTimeout(5000) { second.await() } is OperationResult.Success)
        assertEquals(1, calls.get())
    }

    /** An ambiguous backend response persists UNKNOWN instead of a false definite failure. */
    @Test fun lostAcknowledgmentStaysUnknown() = runBlocking {
        val result = coordinator(CapsulePublicationBackend { throw IOException("Lost response") })
            .publish(draft())
        assertTrue(result is OperationResult.OutcomeUnknown)
        assertEquals(PublicationPhase.OUTCOME_UNKNOWN, journal.load("sender", "one")?.phase)
    }

    /** A previously saved unresolved send cannot be re-created as a new attempt in this step. */
    @Test fun restoredUnknownRequiresReconciliation() = runBlocking {
        val operation = FrozenCapsuleOperation.freeze(draft(), "sender", "UTC", now)
        journal.save(FrozenOperationRecord(operation, PublicationPhase.OUTCOME_UNKNOWN))
        val calls = AtomicInteger()
        assertTrue(coordinator(CapsulePublicationBackend { calls.incrementAndGet(); Unit })
            .publish(draft()) is OperationResult.OutcomeUnknown)
        assertEquals(0, calls.get())
    }

    /** Canceling a screen's waiter does not cancel the process-owned publication task. */
    @Test fun cancelledWaiterDoesNotStartAnotherWrite() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val ack = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val service = coordinator(CapsulePublicationBackend {
            calls.incrementAndGet()
            entered.complete(Unit)
            ack.await()
        })
        val first = async { service.publish(draft()) }
        withTimeout(5000) { entered.await() }
        first.cancelAndJoin()
        assertEquals(PublicationPhase.PUBLISHING, journal.load("sender", "one")?.phase)
        val second = async { service.publish(draft()) }
        ack.complete(Unit)
        assertTrue(withTimeout(5000) { second.await() } is OperationResult.Success)
        assertEquals(1, calls.get())
    }

    /** Another account does not receive a successful callback for the previous sender. */
    @Test fun sessionChangeRejectsSuccessCallback() = runBlocking {
        val service = coordinator(CapsulePublicationBackend { session = SessionSnapshot("other", 2) })
        assertTrue(service.publish(draft()) is OperationResult.Denied)
        assertEquals(PublicationPhase.SENT, journal.load("sender", "one")?.phase)
    }

    /** Changing an in-flight snapshot is rejected without overwriting its durable identity. */
    @Test fun editedRetapCannotReplacePendingContent() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val ack = CompletableDeferred<Unit>()
        val service = coordinator(CapsulePublicationBackend { entered.complete(Unit); ack.await() })
        val first = async { service.publish(draft()) }
        withTimeout(5000) { entered.await() }
        assertTrue(service.publish(draft().copy(text = "Changed")) is OperationResult.Invalid)
        assertEquals("message", journal.load("sender", "one")?.operation?.text)
        ack.complete(Unit)
        withTimeout(5000) { first.await() }
        Unit
    }

    /** A failed initial disk save prevents all network work. */
    @Test fun journalFailurePreventsBackendCall() = runBlocking {
        journal = FrozenOperationJournal(temporary.newFile())
        val calls = AtomicInteger()
        assertTrue(coordinator(CapsulePublicationBackend { calls.incrementAndGet(); Unit })
            .publish(draft()) is OperationResult.Unavailable)
        assertEquals(0, calls.get())
    }

    /** Metadata and protected text share identity/digest and use server-assigned creation time. */
    @Test fun pairedPayloadUsesApprovedSchema() {
        val operation = FrozenCapsuleOperation.freeze(draft(true), "sender", "UTC", now)
        val payload = CapsulePublicationPayload.from(operation)
        assertEquals(operation.snapshotDigest, payload.metadata["snapshotDigest"])
        assertEquals(operation.snapshotDigest, payload.content["snapshotDigest"])
        assertEquals(payload.metadata["capsuleId"], payload.content["capsuleId"])
        assertEquals(payload.metadata["photoPath"], payload.content["photoObjectPath"])
        assertEquals(PublicationServerValue.TIMESTAMP, payload.metadata["createdAt"])
        assertEquals(false, payload.metadata["unlocked"])
        assertFalse(payload.metadata.containsKey("text"))
        assertFalse(payload.metadata.containsKey("title"))
    }

    /** A media fixture returns the same approved bytes/path identity, without network I/O. */
    private open class Media : PublicationMedia {
        /** Returns a durable-upload identity fixture for the selected original. */
        override suspend fun freeze(photo: RetainedOriginalPhoto) = OperationResult.Success(
            FrozenPhotoUpload(photo, "capsuleMedia/${photo.ownerUid}/${photo.capsuleId}/original"))
        /** Confirms only the same path, byte count and hash. */
        override suspend fun upload(photo: FrozenPhotoUpload): OperationResult<PhotoUploadAcknowledgment> =
            OperationResult.Success(PhotoUploadAcknowledgment(photo.objectPath,
                photo.original.sizeBytes, photo.original.sha256))
    }
}
