package com.example.timecapsule.data.publication

import com.example.timecapsule.data.session.StorageSession
import com.example.timecapsule.domain.model.CapsuleDraft
import com.example.timecapsule.domain.model.CapsuleLock
import com.example.timecapsule.domain.model.FrozenCapsuleOperation
import com.example.timecapsule.domain.model.FrozenOperationRecord
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.domain.model.OriginalPhotoFormat
import com.example.timecapsule.domain.model.PublicationPhase
import com.example.timecapsule.domain.model.RetainedOriginalPhoto
import com.example.timecapsule.domain.model.SendStatus
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot
import java.io.IOException
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Deterministic delayed-write and recovery scenarios using real journals and a controlled server. */
class PublicationRecoveryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val now = Instant.parse("2026-11-21T00:00:00Z")
    private lateinit var journal: FrozenOperationJournal
    private lateinit var backend: Backend
    @Volatile private var session = SessionSnapshot("sender", 1)

    /** Isolates durable state and network controls for each scenario. */
    @Before fun setup() {
        journal = FrozenOperationJournal(temporary.newFolder())
        backend = Backend()
    }

    /** Creates the one intended capsule; location-only locks keep replay fixtures time-independent. */
    private fun draft(): CapsuleDraft = CapsuleDraft("one", "recipient", "message", null,
        CapsuleLock(LockMode.LOCATION, location =
            com.example.timecapsule.domain.model.LocationTarget(37.0, 127.0)),
        isRecipientConfirmed = true)

    /** Makes the exact snapshot used by the controlled server. */
    private fun operation(): FrozenCapsuleOperation =
        FrozenCapsuleOperation.freeze(draft(), "sender", "UTC", now)

    /** Injects an operational wait limit without changing capsule identity or unlock rules. */
    private fun service(timeout: Long = 5000) = CapsulePublicationCoordinator(
        journal, backend, null, { session }, { now }, { "UTC" }, timeout)

    /** Persists an unresolved prior write, as would remain after process termination. */
    private fun saveUnknown(operation: FrozenCapsuleOperation = operation(), photoAck: Boolean = false) {
        journal.save(StorageSession(session) { session }, FrozenOperationRecord(operation, PublicationPhase.OUTCOME_UNKNOWN,
            photoAcknowledged = photoAck, hasUnresolvedWrite = true))
    }

    /** A lost response is resolved as SENT by a matching authoritative record. */
    @Test fun lostResponseReconcilesMatchingPublication() = runBlocking {
        backend.onPublish = {
            backend.evidence = PublicationInspection.MATCHING
            throw IOException("Response lost after commit")
        }
        assertTrue(service().publish(draft()) is OperationResult.Success)
        assertEquals(PublicationPhase.SENT, journal.load("sender", "one")?.phase)
        assertEquals(1, backend.writes.size)
    }

    /** After restart, an existing matching record is accepted without another publication. */
    @Test fun matchingRecordAvoidsReplay() = runBlocking {
        saveUnknown()
        backend.evidence = PublicationInspection.MATCHING
        assertTrue(service().retry("one") is OperationResult.Success)
        assertTrue(backend.writes.isEmpty())
    }

    /** An absent record while a delayed write is active remains UNKNOWN, not FAILED. */
    @Test fun absentReadCannotCancelPendingWrite() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val ack = CompletableDeferred<Unit>()
        backend.onPublish = { entered.complete(Unit); ack.await() }
        val service = service()
        val first = async { service.publish(draft()) }
        withTimeout(5000) { entered.await() }
        assertTrue(service.reconcile("one") is OperationResult.OutcomeUnknown)
        assertEquals(PublicationPhase.OUTCOME_UNKNOWN, journal.load("sender", "one")?.phase)
        assertTrue(journal.load("sender", "one")?.hasUnresolvedWrite == true)
        ack.complete(Unit)
        assertTrue(withTimeout(5000) { first.await() } is OperationResult.Success)
        assertEquals(PublicationPhase.SENT, journal.load("sender", "one")?.phase)
    }

    /** A UI timeout leaves work running and Retry joins that same pending operation. */
    @Test fun timeoutDoesNotCreateSecondWrite() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val ack = CompletableDeferred<Unit>()
        backend.onPublish = { entered.complete(Unit); ack.await() }
        val service = service(timeout = 100)
        assertTrue(service.publish(draft()) is OperationResult.OutcomeUnknown)
        withTimeout(5000) { entered.await() }
        val retry = async { service.retry("one") }
        backend.evidence = PublicationInspection.MATCHING
        ack.complete(Unit)
        assertTrue(withTimeout(5000) { retry.await() } is OperationResult.Success)
        assertEquals(1, backend.writes.size)
    }

    /** Restored uncertainty replays only the original ID, snapshot and digest after absence. */
    @Test fun replayUsesUnchangedOperation() = runBlocking {
        val original = operation()
        saveUnknown(original)
        assertTrue(service().retry("one") is OperationResult.Success)
        assertEquals(listOf(original), backend.writes.toList())
    }

    /** A rejected replay cannot rule out an earlier delayed write after another absent read. */
    @Test fun replayRejectionDoesNotEraseOlderUncertainty() = runBlocking {
        saveUnknown()
        backend.onPublish = { throw PublicationRejectedException("Replay rejected") }
        val service = service()
        assertTrue(service.retry("one") is OperationResult.OutcomeUnknown)
        val saved = journal.load("sender", "one")
        assertEquals(PublicationPhase.OUTCOME_UNKNOWN, saved?.phase)
        assertTrue(saved?.hasUnresolvedWrite == true)
        assertTrue(service.reconcile("one") is OperationResult.OutcomeUnknown)
    }

    /** A single terminal rejection with no earlier write and confirmed absence is a definite failure. */
    @Test fun firstTerminalRejectionCanResolveFailure() = runBlocking {
        backend.onPublish = { throw PublicationRejectedException("Rejected") }
        val service = service()
        assertTrue(service.publish(draft()) is OperationResult.Invalid)
        assertEquals(PublicationPhase.FAILED, journal.load("sender", "one")?.phase)
        assertFalse(journal.load("sender", "one")?.hasUnresolvedWrite == true)
        val result = service.reconcile("one") as OperationResult.Success
        assertEquals(SendStatus.FAILED, result.data)
    }

    /** A definite failure still retries with the same ID, never a newly generated capsule. */
    @Test fun definiteFailureRetriesOriginalId() = runBlocking {
        backend.onPublish = { throw PublicationRejectedException("Rejected") }
        val service = service()
        service.publish(draft())
        backend.onPublish = {}
        assertTrue(service.retry("one") is OperationResult.Success)
        assertEquals(listOf("one", "one"), backend.writes.map { it.capsuleId })
        assertEquals(backend.writes.first().snapshotDigest, backend.writes.last().snapshotDigest)
    }

    /** Conflicting remote content is not overwritten or accepted as the intended capsule. */
    @Test fun conflictingRecordPreventsReplay() = runBlocking {
        saveUnknown()
        backend.evidence = PublicationInspection.CONFLICT
        assertTrue(service().retry("one") is OperationResult.Invalid)
        assertTrue(backend.writes.isEmpty())
        assertEquals(PublicationPhase.OUTCOME_UNKNOWN, journal.load("sender", "one")?.phase)
    }

    /** Backend unavailability cannot be interpreted as an authoritative absence. */
    @Test fun unavailableServerPreventsReplay() = runBlocking {
        saveUnknown()
        backend.evidence = PublicationInspection.UNAVAILABLE
        assertTrue(service().retry("one") is OperationResult.OutcomeUnknown)
        assertTrue(backend.writes.isEmpty())
    }

    /** A local pending-write overlay never proves successful backend publication. */
    @Test fun pendingOverlayIsNotSent() = runBlocking {
        saveUnknown()
        backend.evidence = PublicationInspection.PENDING
        assertTrue(service().reconcile("one") is OperationResult.OutcomeUnknown)
        assertTrue(backend.writes.isEmpty())
    }

    /** Metadata match alone cannot invent a missing required photo-upload acknowledgment. */
    @Test fun matchingPhotoWithoutAckStaysUnknown() = runBlocking {
        val photo = RetainedOriginalPhoto("sender", "one",
            "00000000-0000-0000-0000-000000000000.original", OriginalPhotoFormat.PNG, 3,
            "a".repeat(64))
        val original = FrozenCapsuleOperation.freeze(draft().copy(originalPhoto = photo),
            "sender", "UTC", now)
        saveUnknown(original)
        backend.evidence = PublicationInspection.MATCHING
        assertTrue(service().retry("one") is OperationResult.OutcomeUnknown)
        assertTrue(backend.writes.isEmpty())
    }

    /** Persisted photo proof survives journal recreation and avoids uploading a published photo again. */
    @Test fun matchingPhotoWithDurableAckResolvesSent() = runBlocking {
        val photo = RetainedOriginalPhoto("sender", "one",
            "00000000-0000-0000-0000-000000000000.original", OriginalPhotoFormat.PNG, 3,
            "a".repeat(64))
        val original = FrozenCapsuleOperation.freeze(draft().copy(originalPhoto = photo),
            "sender", "UTC", now)
        saveUnknown(original, photoAck = true)
        backend.evidence = PublicationInspection.MATCHING
        assertTrue(service().retry("one") is OperationResult.Success)
        assertTrue(backend.writes.isEmpty())
    }

    /** Account changes during lookup invalidate its callback and prevent replay. */
    @Test fun changedSessionRejectsServerResult() = runBlocking {
        saveUnknown()
        backend.onInspect = { session = SessionSnapshot("other", 2) }
        assertTrue(service().retry("one") is OperationResult.Denied)
        assertTrue(backend.writes.isEmpty())
    }

    /** Failure evidence is recoverable after process recreation, without losing its earlier-write flag. */
    @Test fun rejectionEvidenceSurvivesReload() = runBlocking {
        val original = operation()
        journal.save(StorageSession(session) { session }, FrozenOperationRecord(original, PublicationPhase.OUTCOME_UNKNOWN,
            terminalRejectionConfirmed = true))
        val result = service().reconcile("one") as OperationResult.Success
        assertEquals(SendStatus.FAILED, result.data)
    }

    /** Equal claimed digests cannot conceal an altered immutable field or protected body. */
    @Test fun matcherComparesActualFieldsNotOnlyDigest() {
        val original = operation()
        val payload = CapsulePublicationPayload.from(original)
        val metadata = payload.metadata + ("createdAt" to now)
        assertTrue(PublicationRecordMatcher.matches(original, metadata, payload.content))
        assertFalse(PublicationRecordMatcher.matches(original,
            metadata + ("recipientId" to "other"), payload.content))
        assertFalse(PublicationRecordMatcher.matches(original, metadata,
            payload.content + ("text" to "different")))
    }

    /** Locked self-send recovery does not require weakening protected-content authorization. */
    @Test fun lockedSelfSendMatchesFromPairedMetadata() {
        val original = FrozenCapsuleOperation.freeze(draft().copy(recipientUid = "sender"),
            "sender", "UTC", now)
        val metadata = CapsulePublicationPayload.from(original).metadata + ("createdAt" to now)
        assertTrue(PublicationRecordMatcher.matches(original, metadata, null))
        val cross = operation()
        val crossMetadata = CapsulePublicationPayload.from(cross).metadata + ("createdAt" to now)
        assertFalse(PublicationRecordMatcher.matches(cross, crossMetadata, null))
    }

    /** Already-existing matching content resolves a rejected replay as success, not duplicate failure. */
    @Test fun rejectedReplayReconcilesExistingMatchingRecord() = runBlocking {
        saveUnknown()
        backend.onPublish = {
            backend.evidence = PublicationInspection.MATCHING
            throw PublicationRejectedException("Already exists")
        }
        assertTrue(service().retry("one") is OperationResult.Success)
        assertEquals(1, backend.writes.size)
        assertEquals(PublicationPhase.SENT, journal.load("sender", "one")?.phase)
    }

    /** A confirmed old send keeps its time zone and time after the user travels or its time passes. */
    @Test fun completedSendDoesNotRefreezeExpiredLock() = runBlocking {
        val draft = draft().copy(lock = CapsuleLock(LockMode.TIME, now.plusSeconds(60)))
        val original = FrozenCapsuleOperation.freeze(draft, "sender", "Asia/Seoul", now)
        journal.save(StorageSession(session) { session }, FrozenOperationRecord(original, PublicationPhase.SENT))
        val later = CapsulePublicationCoordinator(journal, backend, null, { session },
            { now.plusSeconds(120) }, { "America/New_York" })
        assertTrue(later.publish(draft) is OperationResult.Success)
        assertEquals(original, journal.load("sender", "one")?.operation)
        assertTrue(backend.writes.isEmpty())
    }

    /** A controlled backend models delayed ACKs and server reads without claiming SDK integration. */
    private class Backend : CapsulePublicationBackend {
        val writes = CopyOnWriteArrayList<FrozenCapsuleOperation>()
        @Volatile var evidence = PublicationInspection.ABSENT
        var onPublish: suspend () -> Unit = {}
        var onInspect: () -> Unit = {}

        /** Records unchanged replay content and performs the requested acknowledgment behavior. */
        override suspend fun publish(operation: FrozenCapsuleOperation) {
            writes += operation
            onPublish()
        }

        /** Returns controlled server-only evidence, including deliberate absence while a write waits. */
        override suspend fun inspect(operation: FrozenCapsuleOperation): PublicationInspection {
            onInspect()
            return evidence
        }
    }
}
