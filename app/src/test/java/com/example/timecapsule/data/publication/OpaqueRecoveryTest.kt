package com.example.timecapsule.data.publication

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

        override suspend fun publish(operation: FrozenCapsuleOperation) {}

        override suspend fun inspect(operation: FrozenCapsuleOperation): PublicationInspection {
            return inspectionResults[operation.capsuleId] ?: PublicationInspection.ABSENT
        }
    }

    private lateinit var fakeBackend: FakeCapsulePublicationBackend
    private lateinit var coordinator: CapsulePublicationCoordinator

    @Before
    fun setUp() {
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
        journal.save(FrozenOperationRecord(op, phase = PublicationPhase.PUBLISHING, photoAcknowledged = true))

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
        journal.save(FrozenOperationRecord(op, phase = PublicationPhase.PUBLISHING, photoAcknowledged = true))

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
}
