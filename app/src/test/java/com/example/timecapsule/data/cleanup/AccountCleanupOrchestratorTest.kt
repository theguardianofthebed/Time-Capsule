package com.example.timecapsule.data.cleanup

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

    private lateinit var fakeNotificationAdapter: FakeNotificationAdapter
    private lateinit var unlockNotificationCoordinator: UnlockNotificationCoordinator

    @Before
    fun setUp() {
        retentionDir = temporaryFolder.newFolder("retained-content")
        journalDir = temporaryFolder.newFolder("capsule-operations")
        photoStoreDir = temporaryFolder.newFolder("original-photos")
        photoDraftDir = temporaryFolder.newFolder("photo-drafts")
        attemptFile = temporaryFolder.newFile("notification-attempts.json")

        retentionAdapter = DefaultLocalRetentionAdapter(retentionDir)
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
            sessionTracker = sessionTracker
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

        // 1. Save retained content
        retentionAdapter.saveRetainedContent(uid, capsuleId1, Instant.now(), "Retained Text", null, null)

        // 2. Save draft
        photoDraftJournal.save(uid, PhotoDraftRecord(capsuleId1, "Draft Title", "Draft Text", null))

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
        journal.save(FrozenOperationRecord(unresolvedOp, phase = PublicationPhase.PUBLISHING))

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
        journal.save(FrozenOperationRecord(sentOp, phase = PublicationPhase.SENT))

        // 5. Record notification attempt
        attemptStore.atomicallyClaimAttempt(uid, capsuleId1)

        // EXECUTE CLEANUP
        orchestrator.cleanupAccount(uid)

        // VERIFY:
        // A. Retained content is cleared
        val readRetained = retentionAdapter.readRetainedContent(uid, capsuleId1)
        assertTrue(readRetained is OperationResult.Denied)

        // B. Photo draft journal is cleared
        assertNull(photoDraftJournal.load(uid))

        // C. SENT operation is deleted from journal
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
}
