package com.example.timecapsule.data.cleanup

import com.example.timecapsule.data.session.StorageSession
import com.example.timecapsule.data.adapter.DefaultLocalRetentionAdapter
import com.example.timecapsule.data.adapter.LocalRetentionAdapter
import com.example.timecapsule.domain.model.OriginalPhotoFormat
import com.example.timecapsule.data.media.OriginalPhotoStore
import com.example.timecapsule.data.media.PhotoDraftJournal
import com.example.timecapsule.data.media.PhotoDraftRecord
import com.example.timecapsule.data.notification.NotificationAdapter
import com.example.timecapsule.data.notification.NotificationAttemptStore
import com.example.timecapsule.data.notification.NotificationPostResult
import com.example.timecapsule.data.notification.UnlockNotificationCoordinator
import com.example.timecapsule.data.publication.FrozenOperationJournal
import com.example.timecapsule.data.session.DefaultSessionTracker
import com.example.timecapsule.domain.model.CapsuleLock
import com.example.timecapsule.domain.model.FrozenCapsuleOperation
import com.example.timecapsule.domain.model.FrozenOperationRecord
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.domain.model.PublicationPhase
import com.example.timecapsule.domain.result.OperationResult
import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

/**
 * Unit tests verifying [AccountCleanupOrchestrator] multi-store cleanup,
 * opaque journal compaction, photo purging, and notification cancellation.
 */
class AccountCleanupOrchestratorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var retentionDir: File
    private lateinit var journalDir: File
    private lateinit var photoStoreDir: File
    private lateinit var photoDraftDir: File
    private lateinit var attemptFile: File

    private lateinit var retentionAdapter: LocalRetentionAdapter
    private lateinit var journal: FrozenOperationJournal
    private lateinit var originalPhotoStore: OriginalPhotoStore
    private lateinit var photoDraftJournal: PhotoDraftJournal
    private lateinit var attemptStore: NotificationAttemptStore
    private lateinit var sessionTracker: DefaultSessionTracker
    private lateinit var orchestrator: AccountCleanupOrchestrator

    private class FakeNotificationAdapter : NotificationAdapter {
        val canceledAccounts = mutableListOf<String>()
        override fun areNotificationsPermitted(): Boolean = true
        override fun createNotificationChannel() {}
        override fun postUnlockNotification(accountUid: String, capsuleId: String) =
            NotificationPostResult.ATTEMPTED_SUCCESS
        override fun cancelAccountNotifications(accountUid: String) {
            canceledAccounts.add(accountUid)
        }
    }

    private lateinit var noBackupDir: File
    private lateinit var frozenPhotosDir: File
    private lateinit var fakeNotificationAdapter: FakeNotificationAdapter
    private lateinit var unlockNotificationCoordinator: UnlockNotificationCoordinator

    @Before
    fun setUp() {
        retentionDir = temporaryFolder.newFolder("retained-content")
        journalDir = temporaryFolder.newFolder("capsule-operations")
        photoStoreDir = temporaryFolder.newFolder("original-photos")
        photoDraftDir = temporaryFolder.newFolder("photo-drafts")
        noBackupDir = temporaryFolder.newFolder("no_backup")
        frozenPhotosDir = temporaryFolder.newFolder("frozen-photos")
        attemptFile = temporaryFolder.newFile("notification-attempts.json")

        retentionAdapter = DefaultLocalRetentionAdapter(retentionDir) { sessionTracker.currentSession() }
        journal = FrozenOperationJournal(journalDir)
        originalPhotoStore = OriginalPhotoStore(photoStoreDir) { OriginalPhotoFormat.JPEG }
        photoDraftJournal = PhotoDraftJournal(photoDraftDir)
        attemptStore = NotificationAttemptStore(attemptFile)
        sessionTracker = DefaultSessionTracker()

        fakeNotificationAdapter = FakeNotificationAdapter()
        unlockNotificationCoordinator = UnlockNotificationCoordinator(
            notificationAdapter = fakeNotificationAdapter,
            attemptStore = attemptStore,
            sessionTracker = sessionTracker
        )

        orchestrator = AccountCleanupOrchestrator(
            retentionAdapter = retentionAdapter,
            journal = journal,
            originalPhotoStore = originalPhotoStore,
            photoDraftJournal = photoDraftJournal,
            unlockNotificationCoordinator = unlockNotificationCoordinator,
            noBackupDir = noBackupDir,
            frozenPhotosDir = frozenPhotosDir
        )
    }

    @After
    fun tearDown() {
        temporaryFolder.delete()
    }

    @Test
    fun testCleanupAccountPurgesRetainedContentAndDraftsAndCompactsJournal() = runBlocking {
        val uid = "user-cleanup-1"
        val capsuleId1 = "capsule-unresolved-1"
        val capsuleId2 = "capsule-sent-2"

        sessionTracker.startSession(uid)
        val session = sessionTracker.currentSession()
        val origin = StorageSession(session, sessionTracker::currentSession)

        // 1. Save retained content
        retentionAdapter.saveRetainedContent(session, capsuleId1, Instant.now(), "Retained Text", null, null)

        // 2. Save draft
        photoDraftJournal.save(origin, uid, PhotoDraftRecord(capsuleId1, "Draft Title", "Draft Text", null))

        // 3. Save unresolved operation in journal
        val rawUnresolved = FrozenCapsuleOperation(
            capsuleId = capsuleId1,
            senderUid = uid,
            recipientUid = "recipient-99",
            title = "Secret Title",
            text = "Secret Message Payload",
            lock = CapsuleLock(LockMode.TIME, unlockAt = Instant.now().plusSeconds(3600)),
            creationZone = "UTC",
            photo = null,
            snapshotDigest = ""
        )
        val unresolvedOp = rawUnresolved.copy(snapshotDigest = rawUnresolved.calculateDigest())
        journal.save(origin, FrozenOperationRecord(unresolvedOp, phase = PublicationPhase.PUBLISHING))

        // 4. Save SENT operation in journal
        val rawSent = FrozenCapsuleOperation(
            capsuleId = capsuleId2,
            senderUid = uid,
            recipientUid = "recipient-99",
            title = "Sent Title",
            text = "Sent Payload",
            lock = CapsuleLock(LockMode.TIME, unlockAt = Instant.now().minusSeconds(3600)),
            creationZone = "UTC",
            photo = null,
            snapshotDigest = ""
        )
        val sentOp = rawSent.copy(snapshotDigest = rawSent.calculateDigest())
        journal.save(origin, FrozenOperationRecord(sentOp, phase = PublicationPhase.SENT))

        // 5. Record notification attempt
        attemptStore.atomicallyClaimAttempt(uid, capsuleId1)

        // 6. Save frozen photo record in real layout: frozen-photos/<capsuleId>/<hashedUid>.properties
        val frozenCapsuleDir = File(frozenPhotosDir, capsuleId1)
        val frozenJournal = PhotoDraftJournal(frozenCapsuleDir)
        frozenJournal.save(origin, uid, PhotoDraftRecord(capsuleId1, "Frozen Title", "Frozen Text", null))
        assertNotNull(frozenJournal.load(uid))

        // EXECUTE CLEANUP
        orchestrator.cleanupAccount(uid)

        // VERIFY:
        // A. Retained content is cleared
        val readRetained = retentionAdapter.readRetainedContent(session, capsuleId1)
        assertTrue(readRetained is OperationResult.Denied)

        // B. Photo draft journal is cleared
        assertNull(photoDraftJournal.load(uid))

        // C. Frozen photo journal in capsule dir is cleared
        assertNull(frozenJournal.load(uid))

        // D. SENT operation is deleted from journal
        assertNull(journal.load(uid, capsuleId2))

        // D. Unresolved operation is COMPACTED to opaque recovery record (text="", title=null)
        val loadedUnresolved = journal.load(uid, capsuleId1)
        assertNotNull(loadedUnresolved)
        assertEquals("", loadedUnresolved!!.operation.text)
        assertNull(loadedUnresolved.operation.title)
        assertEquals(PublicationPhase.PUBLISHING, loadedUnresolved.phase)

        // E. Notifications canceled
        assertTrue(fakeNotificationAdapter.canceledAccounts.contains(uid))
    }

    /** Cleaning B finishes exactly B's obligation, even after recreating the disk reader. */
    @Test fun completingAnotherAccountPreservesEarlierObligation() = runBlocking {
        orchestrator.markCleanupPending("A")
        orchestrator.markCleanupPending("B")
        assertTrue(orchestrator.cleanupAccount("B") is OperationResult.Success)
        val reopened = AccountCleanupOrchestrator(retentionAdapter, noBackupDir = noBackupDir)
        assertEquals("A", reopened.getPendingCleanupUid())
        assertTrue(reopened.cleanupAccount("A") is OperationResult.Success)
        assertFalse(reopened.hasPendingCleanup())
    }

    @Test
    fun testInterruptedCleanupRecoveryOnStartup() = runBlocking {
        val interruptedUid = "user-interrupted-1"
        sessionTracker.startSession(interruptedUid)
        val origin = sessionTracker.currentSession()
        retentionAdapter.saveRetainedContent(origin, "capsule-int-1", Instant.now(), "Content Interrupted", null, null)

        // Simulate process death after pending marker was written
        val markerFile = File(noBackupDir, "pending-cleanup.properties")
        markerFile.writeText("pendingUid=$interruptedUid\n")
        assertTrue(markerFile.exists())

        // Run startup recovery
        val res = orchestrator.cleanupAccount("")
        assertTrue(res is OperationResult.Success)

        // Pending marker must be deleted after successful recovery
        assertFalse(markerFile.exists())

        // Content of interrupted user must be purged
        val readRes = retentionAdapter.readRetainedContent(origin, "capsule-int-1")
        assertTrue(readRes is OperationResult.Denied)
    }

    @Test
    fun testFailingCleanupFailsClosedAndPreservesPendingMarker() = runBlocking {
        val failingAdapter = object : LocalRetentionAdapter by retentionAdapter {
            override suspend fun clearAccountData(uid: String) {
                throw IOException("Injected retention storage failure")
            }
        }
        val failingOrchestrator = AccountCleanupOrchestrator(
            retentionAdapter = failingAdapter,
            noBackupDir = noBackupDir
        )

        val res = failingOrchestrator.cleanupAccount("user-fail-1")
        assertTrue(res is OperationResult.Unavailable)

        val markerFile = File(noBackupDir, "pending-cleanup.properties")
        assertTrue("Pending marker must be preserved when cleanup fails", markerFile.exists())
    }
}
