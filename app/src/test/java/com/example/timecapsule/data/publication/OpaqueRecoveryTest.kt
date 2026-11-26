package com.example.timecapsule.data.publication

import com.example.timecapsule.data.session.fixtureSession
import com.example.timecapsule.domain.model.CapsuleLock
import com.example.timecapsule.domain.model.FrozenCapsuleOperation
import com.example.timecapsule.domain.model.FrozenOperationRecord
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.domain.model.PublicationPhase
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot
import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests verifying opaque recovery record compaction, payload scrubbing,
 * and server reconciliation after logout across account transitions.
 */
class OpaqueRecoveryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var journalDir: File
    private lateinit var journal: FrozenOperationJournal

    private class FakeCapsulePublicationBackend : CapsulePublicationBackend {
        val inspectionResults = mutableMapOf<String, PublicationInspection>()
        var publishCalls = 0

        override suspend fun publish(operation: FrozenCapsuleOperation) {
            publishCalls++
        }

        override suspend fun inspect(operation: FrozenCapsuleOperation): PublicationInspection {
            return inspectionResults[operation.capsuleId] ?: PublicationInspection.ABSENT
        }
    }

    private lateinit var fakeBackend: FakeCapsulePublicationBackend
    private lateinit var coordinator: CapsulePublicationCoordinator

    @Before
    fun setUp() {
        CapsulePublicationCoordinator.clearActiveAttemptsForTesting()
        journalDir = temporaryFolder.newFolder("capsule-operations")
        journal = FrozenOperationJournal(journalDir)
        fakeBackend = FakeCapsulePublicationBackend()

        val activeSession = SessionSnapshot(uid = "user-sender-1", sessionGeneration = 1L)
        coordinator = CapsulePublicationCoordinator(
            journal = journal,
            backend = fakeBackend,
            media = null,
            session = { activeSession }
        )
    }

    @After
    fun tearDown() {
        CapsulePublicationCoordinator.clearActiveAttemptsForTesting()
        temporaryFolder.delete()
    }

    @Test
    fun testJournalCompactionScrubsPayloadAndRetainsOpaqueMetadata() = runBlocking {
        val uid = "user-sender-1"
        val capsuleId = "capsule-unresolved-100"

        val rawOp = FrozenCapsuleOperation(
            capsuleId = capsuleId,
            senderUid = uid,
            recipientUid = "recipient-200",
            title = "Sensitive Title",
            text = "Sensitive Private Text Message",
            lock = CapsuleLock(LockMode.TIME, unlockAt = Instant.now().plusSeconds(3600)),
            creationZone = "UTC",
            photo = null,
            snapshotDigest = ""
        )
        val op = rawOp.copy(snapshotDigest = rawOp.calculateDigest())
        journal.save(fixtureSession(uid), FrozenOperationRecord(op, phase = PublicationPhase.PUBLISHING, photoAcknowledged = true))

        // Compact account data to opaque (simulate logout)
        val compacted = journal.compactAccountDataToOpaque(uid)
        assertTrue(compacted.contains(capsuleId))

        // Verify loaded record is scrubbed
        val loaded = journal.load(uid, capsuleId)
        assertNotNull(loaded)
        val loadedOp = loaded!!.operation
        assertTrue(loadedOp.isOpaque)
        assertEquals("", loadedOp.text)
        assertNull(loadedOp.title)
        assertEquals(uid, loadedOp.senderUid)
        assertEquals(op.snapshotDigest, loadedOp.snapshotDigest)
        assertTrue(loaded.photoAcknowledged)
    }

    @Test
    fun testReconcileOpaqueOperationWithServerMatchingResolvesSent() = runBlocking {
        val uid = "user-sender-1"
        val capsuleId = "capsule-unresolved-200"

        val rawOp = FrozenCapsuleOperation(
            capsuleId = capsuleId,
            senderUid = uid,
            recipientUid = "recipient-200",
            title = "Title",
            text = "Text",
            lock = CapsuleLock(LockMode.TIME, unlockAt = Instant.now().plusSeconds(3600)),
            creationZone = "UTC",
            photo = null,
            snapshotDigest = ""
        )
        val op = rawOp.copy(snapshotDigest = rawOp.calculateDigest())
        journal.save(fixtureSession(uid), FrozenOperationRecord(op, phase = PublicationPhase.PUBLISHING, photoAcknowledged = true))

        // Compact to opaque
        journal.compactAccountDataToOpaque(uid)

        // Mock server inspection as MATCHING
        fakeBackend.inspectionResults[capsuleId] = PublicationInspection.MATCHING

        // Reconcile
        val session = SessionSnapshot(uid = uid, sessionGeneration = 1L)
        val res = coordinator.reconcile(capsuleId, session)

        assertTrue(res is OperationResult.Success)
        val loadedAfter = journal.load(uid, capsuleId)
        assertNotNull(loadedAfter)
        assertEquals(PublicationPhase.SENT, loadedAfter!!.phase)
    }

    @Test
    fun testOpaqueReconciliationWithoutTitleMatchesServer() = runBlocking {
        val uid = "user-sender-1"
        val capsuleId = "capsule-notitle-300"

        val rawOp = FrozenCapsuleOperation(
            capsuleId = capsuleId,
            senderUid = uid,
            recipientUid = "recipient-300",
            title = null,
            text = "No Title Message",
            lock = CapsuleLock(LockMode.TIME, unlockAt = Instant.now().plusSeconds(3600)),
            creationZone = "UTC",
            photo = null,
            snapshotDigest = ""
        )
        val op = rawOp.copy(snapshotDigest = rawOp.calculateDigest())
        journal.save(fixtureSession(uid), FrozenOperationRecord(op, phase = PublicationPhase.PUBLISHING, photoAcknowledged = true))

        journal.compactAccountDataToOpaque(uid)
        fakeBackend.inspectionResults[capsuleId] = PublicationInspection.MATCHING

        val session = SessionSnapshot(uid = uid, sessionGeneration = 1L)
        val res = coordinator.reconcile(capsuleId, session)

        assertTrue(res is OperationResult.Success)
        val loadedAfter = journal.load(uid, capsuleId)
        assertNotNull(loadedAfter)
        assertEquals(PublicationPhase.SENT, loadedAfter!!.phase)
    }

    @Test
    fun testOpaqueRetryWithAbsentServerStateDoesNotRepublish() = runBlocking {
        val uid = "user-sender-1"
        val capsuleId = "capsule-absent-400"

        val rawOp = FrozenCapsuleOperation(
            capsuleId = capsuleId,
            senderUid = uid,
            recipientUid = "recipient-400",
            title = "Test",
            text = "Text",
            lock = CapsuleLock(LockMode.TIME, unlockAt = Instant.now().plusSeconds(3600)),
            creationZone = "UTC",
            photo = null,
            snapshotDigest = ""
        )
        val op = rawOp.copy(snapshotDigest = rawOp.calculateDigest())
        journal.save(fixtureSession(uid), FrozenOperationRecord(op, phase = PublicationPhase.FAILED, terminalRejectionConfirmed = true))

        journal.compactAccountDataToOpaque(uid)
        fakeBackend.inspectionResults[capsuleId] = PublicationInspection.ABSENT

        val session = SessionSnapshot(uid = uid, sessionGeneration = 1L)
        val res = coordinator.retry(capsuleId, session)

        assertEquals("Zero publish calls expected for opaque retry", 0, fakeBackend.publishCalls)
        assertTrue("Expected OutcomeUnknown, Denied, or Unavailable but got $res", res is OperationResult.OutcomeUnknown || res is OperationResult.Denied || res is OperationResult.Unavailable)
        val loadedAfter = journal.load(uid, capsuleId)
        assertNotNull(loadedAfter)
        assertTrue(loadedAfter!!.operation.isOpaque)
        assertEquals("", loadedAfter.operation.text)
    }

    @Test
    fun testRealMatcherWithOpaqueRecord() {
        val rawOp = FrozenCapsuleOperation(
            capsuleId = "capsule-opaque-matcher",
            senderUid = "sender-1",
            recipientUid = "recipient-1",
            title = "Title",
            text = "Secret Text",
            lock = CapsuleLock(LockMode.TIME, unlockAt = Instant.now().plusSeconds(3600)),
            creationZone = "UTC",
            photo = null,
            snapshotDigest = ""
        )
        val op = rawOp.copy(snapshotDigest = rawOp.calculateDigest(), isOpaque = true, text = "", title = null)

        val matchingDoc = mapOf(
            "capsuleId" to "capsule-opaque-matcher",
            "senderId" to "sender-1",
            "snapshotDigest" to op.snapshotDigest,
            "lockMode" to "TIME",
            "creationZone" to "UTC",
            "createdAt" to Instant.now(),
            "unlocked" to false
        )

        assertTrue(PublicationRecordMatcher.matchesOpaque(op, matchingDoc))

        val mismatchedDoc = matchingDoc + ("snapshotDigest" to "different-digest")
        Assert.assertFalse(PublicationRecordMatcher.matchesOpaque(op, mismatchedDoc))
    }

    @Test
    fun testAbsentServerReadPreservesUncertaintyForInFlightWrite() = runBlocking {
        val uid = "user-sender-1"
        val capsuleId = "capsule-inflight-500"

        val rawOp = FrozenCapsuleOperation(
            capsuleId = capsuleId,
            senderUid = uid,
            recipientUid = "recipient-500",
            title = "In-Flight Title",
            text = "In-Flight Text",
            lock = CapsuleLock(LockMode.TIME, unlockAt = Instant.now().plusSeconds(3600)),
            creationZone = "UTC",
            photo = null,
            snapshotDigest = ""
        )
        val op = rawOp.copy(snapshotDigest = rawOp.calculateDigest())
        journal.save(fixtureSession(uid), FrozenOperationRecord(op, phase = PublicationPhase.PUBLISHING, hasUnresolvedWrite = true))

        journal.compactAccountDataToOpaque(uid)
        fakeBackend.inspectionResults[capsuleId] = PublicationInspection.ABSENT

        val session = SessionSnapshot(uid = uid, sessionGeneration = 1L)
        val res = coordinator.reconcile(capsuleId, session)

        // Must remain OutcomeUnknown to preserve earlier write uncertainty
        assertTrue(res is OperationResult.OutcomeUnknown)
        val loadedAfter = journal.load(uid, capsuleId)
        assertNotNull(loadedAfter)
        assertEquals(PublicationPhase.OUTCOME_UNKNOWN, loadedAfter!!.phase)
        assertTrue(loadedAfter.hasUnresolvedWrite)
    }
}
