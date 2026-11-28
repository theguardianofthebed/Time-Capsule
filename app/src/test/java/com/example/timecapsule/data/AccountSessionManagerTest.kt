package com.example.timecapsule.data

import org.junit.Assert.assertThrows
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import com.example.timecapsule.domain.model.CapsuleDraft
import com.example.timecapsule.data.publication.PrivatePublicationDrafts
import com.example.timecapsule.data.publication.CapsulePublicationBackend
import com.example.timecapsule.data.publication.CapsulePublicationCoordinator
import com.example.timecapsule.data.media.PhotoDraftRecord
import com.example.timecapsule.data.session.SessionTracker
import com.example.timecapsule.data.session.StorageSession
import com.example.timecapsule.data.adapter.AuthAdapter
import com.example.timecapsule.data.adapter.DefaultLocalRetentionAdapter
import com.example.timecapsule.data.adapter.FirestoreAdapter
import com.example.timecapsule.data.cleanup.AccountCleanupOrchestrator
import com.example.timecapsule.data.media.OriginalPhotoStore
import com.example.timecapsule.data.media.PhotoDraftJournal
import com.example.timecapsule.data.publication.FrozenOperationJournal
import com.example.timecapsule.data.session.DefaultSessionTracker
import com.example.timecapsule.domain.model.CapsuleLock
import com.example.timecapsule.domain.model.FrozenCapsuleOperation
import com.example.timecapsule.domain.model.FrozenOperationRecord
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.domain.model.OriginalPhotoFormat
import com.example.timecapsule.domain.model.PublicationPhase
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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
 * Unit tests for [AccountSessionManager], session generation single updates,
 * delayed authentication stale callback rejection, and Profile Repair.
 */
class AccountSessionManagerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var sessionTracker: DefaultSessionTracker
    private lateinit var fakeAuthAdapter: FakeAuthAdapter
    private lateinit var fakeFirestoreAdapter: FakeFirestoreAdapter
    private lateinit var accountSessionManager: AccountSessionManager

    private class FakeAuthAdapter : AuthAdapter {
        var uid: String? = null
        var email: String? = null
        var displayName: String? = null
        var failDisplayNameUpdateInSignUp = false

        override fun activeUid(): String? = uid
        override fun activeEmail(): String? = email
        override fun activeDisplayName(): String? = displayName

        override suspend fun signIn(email: String, password: String): OperationResult<String> {
            if (email.isBlank() || password.isBlank()) {
                return OperationResult.Invalid("Email and password must not be blank.")
            }
            if (password == "wrong") {
                return OperationResult.Invalid("Invalid credentials provided.")
            }
            val newUid = if (email.contains("userB")) "user-b" else "user-123"
            uid = newUid
            this.email = email
            this.displayName = "User $newUid"
            return OperationResult.Success(newUid)
        }

        override suspend fun signUp(
            email: String,
            password: String,
            displayName: String
        ): OperationResult<String> {
            if (email.isBlank() || password.isBlank()) {
                return OperationResult.Invalid("Email and password must not be blank.")
            }
            val newUid = "user-123"
            uid = newUid
            this.email = email
            if (failDisplayNameUpdateInSignUp) {
                this.displayName = null // Simulates failure during user.updateProfile()
            } else {
                this.displayName = displayName
            }
            return OperationResult.Success(newUid)
        }

        override suspend fun updateDisplayName(displayName: String): OperationResult<Unit> {
            if (uid == null) {
                return OperationResult.Denied("No active authenticated session available.")
            }
            this.displayName = displayName
            return OperationResult.Success(Unit)
        }

        override fun signOut() {
            uid = null
            email = null
            displayName = null
        }
    }

    private class FakeFirestoreAdapter : FirestoreAdapter {
        val directory = mutableMapOf<String, RecipientProfile>()
        var failSaveProfile = false

        override fun isAvailable(): Boolean = true
        override fun firestoreInstance(): FirebaseFirestore? = null

        override suspend fun getRecipientProfile(uid: String): OperationResult<RecipientProfile> {
            val profile = directory[uid]
            return if (profile != null) {
                OperationResult.Success(profile)
            } else {
                OperationResult.Invalid("No recipient found with UID: $uid")
            }
        }

        override suspend fun saveRecipientProfile(profile: RecipientProfile): OperationResult<Unit> {
            if (failSaveProfile) {
                return OperationResult.Unavailable("Firestore write failed.")
            }
            directory[profile.uid] = profile
            return OperationResult.Success(Unit)
        }
    }

    @Before
    fun setUp() {
        sessionTracker = DefaultSessionTracker()
        fakeAuthAdapter = FakeAuthAdapter()
        fakeFirestoreAdapter = FakeFirestoreAdapter()
        accountSessionManager = AccountSessionManager(
            authAdapter = fakeAuthAdapter,
            firestoreAdapter = fakeFirestoreAdapter,
            sessionTracker = sessionTracker
        )
    }

    @Test
    fun testSignInUpdatesSessionTrackerExactlyOnce() = runBlocking {
        assertEquals(1L, sessionTracker.currentSession().sessionGeneration)
        assertFalse(sessionTracker.currentSession().isAuthenticated)

        val result = accountSessionManager.signIn("user@example.com", "validPass")
        assertTrue(result is OperationResult.Success)

        // SessionTracker must be updated to authenticated state with incremented generation
        assertEquals(2L, sessionTracker.currentSession().sessionGeneration)
        assertTrue(sessionTracker.currentSession().isAuthenticated)
        assertEquals("user-123", sessionTracker.currentSession().uid)
    }

    @Test
    fun testSignUpAndProfileCreationStartsSessionAndSavesProfile() = runBlocking {
        val result = accountSessionManager.signUpAndCreateProfile("new@example.com", "pass123", "New User")
        assertTrue(result is OperationResult.Success)

        // Session started
        assertEquals(2L, sessionTracker.currentSession().sessionGeneration)
        assertEquals("user-123", sessionTracker.currentSession().uid)

        // Firestore profile created
        val profileRes = fakeFirestoreAdapter.getRecipientProfile("user-123")
        assertTrue(profileRes is OperationResult.Success)
        assertEquals("New User", (profileRes as OperationResult.Success).data.displayName)
    }

    @Test
    fun testSignUpAuthSuccess_displayNameUpdateFails_directorySucceeds_activatesSessionAndCompletes() = runBlocking {
        fakeAuthAdapter.failDisplayNameUpdateInSignUp = true
        fakeFirestoreAdapter.failSaveProfile = false

        val result = accountSessionManager.signUpAndCreateProfile("new@example.com", "pass123", "New User")
        assertTrue(result is OperationResult.Success)

        // Session is activated for user-123
        assertTrue(sessionTracker.currentSession().isAuthenticated)
        assertEquals("user-123", sessionTracker.currentSession().uid)

        // Firestore directory profile saved successfully
        val profileRes = fakeFirestoreAdapter.getRecipientProfile("user-123")
        assertTrue(profileRes is OperationResult.Success)
        assertEquals("New User", (profileRes as OperationResult.Success).data.displayName)

        // Auth display name can subsequently be repaired
        val repairRes = accountSessionManager.repairProfile("New User", sessionTracker.currentSession())
        assertTrue(repairRes is OperationResult.Success)
        assertEquals("New User", fakeAuthAdapter.activeDisplayName())
    }

    @Test
    fun testSignUpAuthSuccess_displayNameUpdateFails_directoryFails_activatesSessionAndEnablesProfileRepair() = runBlocking {
        fakeAuthAdapter.failDisplayNameUpdateInSignUp = true
        fakeFirestoreAdapter.failSaveProfile = true

        val result = accountSessionManager.signUpAndCreateProfile("new@example.com", "pass123", "New User")
        assertTrue(result is OperationResult.Unavailable)

        // Auth account created and session activated for user-123
        assertTrue(sessionTracker.currentSession().isAuthenticated)
        assertEquals("user-123", sessionTracker.currentSession().uid)

        // Profile repair can now complete display name and Firestore profile for the same Auth account
        fakeFirestoreAdapter.failSaveProfile = false
        val repairRes = accountSessionManager.repairProfile("New User Repaired", sessionTracker.currentSession())
        assertTrue(repairRes is OperationResult.Success)
        assertEquals("New User Repaired", fakeAuthAdapter.activeDisplayName())
    }

    @Test
    fun testUnauthenticatedProfileRepairIsDenied() = runBlocking {
        // Unauthenticated session
        sessionTracker.clearSession()
        fakeAuthAdapter.signOut()

        val repairRes = accountSessionManager.repairProfile("Repaired Name")
        assertTrue(repairRes is OperationResult.Denied)
    }

    @Test
    fun testAuthenticatedProfileRepairSucceeds() = runBlocking {
        // Authenticated session
        accountSessionManager.signIn("user@example.com", "pass123")

        val repairRes = accountSessionManager.repairProfile("Repaired Name")
        assertTrue(repairRes is OperationResult.Success)

        // Check profile updated in Auth and Firestore
        assertEquals("Repaired Name", fakeAuthAdapter.activeDisplayName())
        val profileRes = fakeFirestoreAdapter.getRecipientProfile("user-123")
        assertTrue(profileRes is OperationResult.Success)
        assertEquals("Repaired Name", (profileRes as OperationResult.Success).data.displayName)
    }

    @Test
    fun testDelayedSignInIsRejectedIfSessionChangesDuringAuthentication() = runBlocking {
        sessionTracker.startSession("user-a")

        val job = async {
            val delayedAuth = object : AuthAdapter by fakeAuthAdapter {
                override suspend fun signIn(email: String, password: String): OperationResult<String> {
                    delay(50)
                    return fakeAuthAdapter.signIn(email, password)
                }
            }
            val delayedManager = AccountSessionManager(delayedAuth, fakeFirestoreAdapter, sessionTracker)
            delayedManager.signIn("user@example.com", "validPass")
        }

        // Session cleared/changed while sign-in was in-flight
        delay(10)
        sessionTracker.clearSession()

        val result = job.await()
        assertTrue("Delayed sign-in must be rejected when session changes during operation", result is OperationResult.Denied)
    }

    @Test
    fun testStaleCallbackFromUserADoesNotSignOutValidUserB() = runBlocking {
        val delayedAuth = object : AuthAdapter by fakeAuthAdapter {
            override suspend fun signIn(email: String, password: String): OperationResult<String> {
                delay(50)
                return OperationResult.Success("user-a")
            }
        }
        val manager = AccountSessionManager(delayedAuth, fakeFirestoreAdapter, sessionTracker)

        // User A starts sign in
        val jobA = async { manager.signIn("userA@example.com", "passA") }

        // User B signs in immediately after, updating sessionTracker to user-b
        delay(10)
        sessionTracker.startSession("user-b")
        fakeAuthAdapter.uid = "user-b"

        // Delayed sign in for User A finishes
        val resultA = jobA.await()

        assertTrue("User A's delayed sign in must be denied as stale", resultA is OperationResult.Denied)
        assertTrue("User B's session must remain valid", sessionTracker.currentSession().isAuthenticated)
        assertEquals("user-b", sessionTracker.currentSession().uid)
        assertEquals("user-b", fakeAuthAdapter.activeUid())
    }

    @Test
    fun testDelayedAccountASignInWithSdkSideEffectCleansUpInconsistentState() = runBlocking {
        val delayedAuth = object : AuthAdapter by fakeAuthAdapter {
            override suspend fun signIn(email: String, password: String): OperationResult<String> {
                delay(50)
                fakeAuthAdapter.uid = "user-a" // Mimic SDK side effect mutating active UID to user-a
                return OperationResult.Success("user-a")
            }
        }
        val manager = AccountSessionManager(delayedAuth, fakeFirestoreAdapter, sessionTracker)

        // User A starts delayed sign-in
        val jobA = async { manager.signIn("userA@example.com", "passA") }

        // While User A is in-flight, SessionTracker is invalidated by a sign-out
        delay(10)
        sessionTracker.clearSession()

        val resultA = jobA.await()

        assertTrue("Stale User A sign-in must be denied", resultA is OperationResult.Denied)
        assertNull("Stale SDK active UID must be signed out from Auth", fakeAuthAdapter.activeUid())
        assertFalse("SessionTracker must remain unauthenticated", sessionTracker.currentSession().isAuthenticated)
    }

    @Test
    fun testLogoutOccursWhileSignInIsInProgress() = runBlocking {
        val delayedAuth = object : AuthAdapter by fakeAuthAdapter {
            override suspend fun signIn(email: String, password: String): OperationResult<String> {
                delay(50)
                fakeAuthAdapter.uid = "user-in-flight"
                return OperationResult.Success("user-in-flight")
            }
        }
        val manager = AccountSessionManager(delayedAuth, fakeFirestoreAdapter, sessionTracker)

        val job = async { manager.signIn("user@example.com", "pass123") }

        delay(10)
        manager.signOut()

        val result = job.await()
        assertTrue("Sign-in must be denied when sign-out occurs during execution", result is OperationResult.Denied)
        assertNull("Auth must be signed out", fakeAuthAdapter.activeUid())
        assertFalse("SessionTracker must be unauthenticated", sessionTracker.currentSession().isAuthenticated)
    }

    @Test
    fun testSignOutTriggersOnAccountClearedCallback() {
        var clearedAccountUid: String? = null
        val manager = AccountSessionManager(
            fakeAuthAdapter,
            fakeFirestoreAdapter,
            sessionTracker,
            onAccountCleared = {
                clearedAccountUid = it
                OperationResult.Success(Unit)
            }
        )

        fakeAuthAdapter.uid = "user-101"
        sessionTracker.startSession("user-101")

        runBlocking { manager.signOut() }

        assertEquals("user-101", clearedAccountUid)
        assertFalse(sessionTracker.currentSession().isAuthenticated)
    }

    @Test
    fun testSignOutFailureFailsClosedAndRetriesOnSecondAttempt() = runBlocking {
        var cleanupAttempts = 0
        var shouldFailCleanup = true
        var isCleanupPending = false

        val manager = AccountSessionManager(
            authAdapter = fakeAuthAdapter,
            firestoreAdapter = fakeFirestoreAdapter,
            sessionTracker = sessionTracker,
            onAccountCleared = { uid ->
                cleanupAttempts++
                if (shouldFailCleanup) {
                    isCleanupPending = true
                    OperationResult.Unavailable("Cleanup failed")
                } else {
                    isCleanupPending = false
                    OperationResult.Success(Unit)
                }
            },
            isCleanupPending = { isCleanupPending }
        )

        fakeAuthAdapter.uid = "user-failing-signout"
        sessionTracker.startSession("user-failing-signout")

        // First sign-out attempt fails
        val firstSignOut = manager.signOut()
        assertTrue(firstSignOut is OperationResult.Unavailable)
        assertFalse("SessionTracker must be unauthenticated after sign-out start", sessionTracker.currentSession().isAuthenticated)
        assertEquals(1, cleanupAttempts)

        // Fix the store failure and attempt second sign-out
        shouldFailCleanup = false
        val secondSignOut = manager.signOut()
        assertTrue(secondSignOut is OperationResult.Success)
        assertNull(fakeAuthAdapter.activeUid())
        assertFalse(sessionTracker.currentSession().isAuthenticated)
        assertEquals(2, cleanupAttempts)
    }

    @Test
    fun testPendingCleanupBlocksSignIn() = runBlocking {
        val isCleanupPending = true
        val manager = AccountSessionManager(
            authAdapter = fakeAuthAdapter,
            firestoreAdapter = fakeFirestoreAdapter,
            sessionTracker = sessionTracker,
            onAccountCleared = {
                if (isCleanupPending) OperationResult.Unavailable("Pending cleanup")
                else OperationResult.Success(Unit)
            },
            isCleanupPending = { isCleanupPending }
        )

        val signInRes = manager.signIn("user@example.com", "password123")
        assertTrue("Sign in must be blocked when cleanup is pending", signInRes is OperationResult.Unavailable)
        assertFalse(sessionTracker.currentSession().isAuthenticated)
    }

    @Test
    fun testRealProductionSignOutWithUnresolvedPublicationSucceedsAndPermitsSubsequentSignIn() = runBlocking {
        val testFolder = temporaryFolder.newFolder("prod-cleanup-test")
        val journalDir = File(testFolder, "capsule-operations")
        val retentionDir = File(testFolder, "retained-content")
        val photoStoreDir = File(testFolder, "original-photos")
        val photoDraftDir = File(testFolder, "photo-drafts")
        val noBackupDir = File(testFolder, "no_backup")

        val journal = FrozenOperationJournal(journalDir)
        val retentionAdapter = DefaultLocalRetentionAdapter(retentionDir) { sessionTracker.currentSession() }
        val photoStore = OriginalPhotoStore(photoStoreDir) { OriginalPhotoFormat.JPEG }
        val draftJournal = PhotoDraftJournal(photoDraftDir)

        val orchestrator = AccountCleanupOrchestrator(
            retentionAdapter = retentionAdapter,
            journal = journal,
            originalPhotoStore = photoStore,
            photoDraftJournal = draftJournal,
            unlockNotificationCoordinator = null,
            noBackupDir = noBackupDir,
            frozenPhotosDir = File(noBackupDir, "frozen-photos")
        )

        val observer = object : SessionTracker by sessionTracker {
            override fun clearSession() {
                assertEquals(currentSession().uid, orchestrator.getPendingCleanupUid())
                assertTrue(File(noBackupDir, "pending-cleanup.properties").isFile)
                sessionTracker.clearSession()
            }
        }
        val manager = orchestrator.sessionManager(fakeAuthAdapter, fakeFirestoreAdapter, observer)

        // 1. User A signs in
        fakeAuthAdapter.uid = "user-sender-A"
        sessionTracker.startSession("user-sender-A")

        // 2. Seed an unresolved publication for User A
        val rawOp = FrozenCapsuleOperation(
            capsuleId = "capsule-unresolved-999",
            senderUid = "user-sender-A",
            recipientUid = "recipient-B",
            title = "Secret Title",
            text = "Secret Message Text",
            lock = CapsuleLock(LockMode.TIME, unlockAt = Instant.now().plusSeconds(3600)),
            creationZone = "UTC",
            photo = null,
            snapshotDigest = ""
        )
        val op = rawOp.copy(snapshotDigest = rawOp.calculateDigest())
        val original = sessionTracker.currentSession()
        val write = StorageSession(original, sessionTracker::currentSession)
        journal.save(write, FrozenOperationRecord(op, phase = PublicationPhase.PUBLISHING))
        val photo = photoStore.retain(write, op.senderUid, op.capsuleId, "image/jpeg", byteArrayOf(1).inputStream())
        draftJournal.save(write, op.senderUid, PhotoDraftRecord(op.capsuleId, "title", "body", photo))
        assertTrue(retentionAdapter.saveRetainedContent(original, op.capsuleId, Instant.now(),
            "verified", null, null) is OperationResult.Success)
        assertTrue(retentionDir.walk().any { it.name == "content.text" })

        // 3. User A signs out
        val signOutRes = manager.signOut()

        // 4. VERIFY:
        // A. Sign-out MUST succeed
        assertTrue("Sign-out must succeed with unresolved publication", signOutRes is OperationResult.Success)
        assertNull(fakeAuthAdapter.activeUid())
        assertFalse(sessionTracker.currentSession().isAuthenticated)

        assertFalse(photoStore.file(photo).exists())
        assertNull(draftJournal.load(op.senderUid))
        assertFalse(retentionDir.walk().any { it.isFile })

        // B. Sensitive payload MUST be compacted to opaque record (text="", title=null)
        val loadedRecord = journal.load("user-sender-A", "capsule-unresolved-999")
        assertNotNull(loadedRecord)
        assertTrue(loadedRecord!!.operation.isOpaque)
        assertEquals("", loadedRecord.operation.text)
        assertNull(loadedRecord.operation.title)

        // C. Subsequent user sign-in via manager.signIn MUST succeed
        val signInRes = manager.signIn("userB@example.com", "passB123")
        assertTrue("Subsequent user sign-in must succeed after cleanup", signInRes is OperationResult.Success)
        val userBUid = (signInRes as OperationResult.Success).data
        assertEquals(userBUid, sessionTracker.currentSession().uid)
        assertFalse(orchestrator.hasPendingCleanup())

        // D. User A's former protected content is inaccessible to User B
        val userBSession = sessionTracker.currentSession()
        val readUserB = retentionAdapter.readRetainedContent(userBSession, "capsule-unresolved-999")
        assertTrue("Former user's protected content must be inaccessible to new user", readUserB is OperationResult.Denied)
    }

    /** Pauses production publication and provider input before their final write, then logs the same UID back in. */
    @Test fun delayedWritesCannotRecreateFilesAfterSameUidLogin() = runBlocking {
        val root = temporaryFolder.newFolder()
        val ops = FrozenOperationJournal(File(root, "operations"))
        val drafts = PhotoDraftJournal(File(root, "drafts"))
        val photos = OriginalPhotoStore(File(root, "photos")) { OriginalPhotoFormat.JPEG }
        val retained = DefaultLocalRetentionAdapter(File(root, "retained"), sessionTracker::currentSession)
        val cleanup = AccountCleanupOrchestrator(retained, ops, photos, drafts, noBackupDir = root)
        val manager = cleanup.sessionManager(fakeAuthAdapter, fakeFirestoreAdapter, sessionTracker)
        val uid = "user-123"
        assertEquals(OperationResult.Success(uid), manager.signIn("a@example.com", "validPass"))
        val first = manager.currentSession()
        val write = StorageSession(first, manager::currentSession)
        val clockEntered = CompletableDeferred<Unit>()
        val inputEntered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val now = Instant.parse("2026-11-26T10:00:00Z")
        val coordinator = CapsulePublicationCoordinator(ops,
            CapsulePublicationBackend { throw AssertionError("Stale publication reached backend") },
            null, manager::currentSession, now = {
                clockEntered.complete(Unit)
                check(release.await(10, TimeUnit.SECONDS))
                now
            })
        val message = CapsuleDraft("one", "recipient", "private", null,
            CapsuleLock(LockMode.TIME, now.plusSeconds(3600)), isRecipientConfirmed = true)
        val draft = PhotoDraftRecord("one", "title", "body", null)
        drafts.save(write, uid, draft)
        val send = async { coordinator.publish(message, first) }
        val input = object : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
            override fun read(bytes: ByteArray, off: Int, len: Int): Int {
                inputEntered.complete(Unit)
                check(release.await(10, TimeUnit.SECONDS))
                return super.read(bytes, off, len)
            }
        }
        val selection = async(Dispatchers.IO) {
            runCatching { photos.retain(write, uid, "one", "image/jpeg", input) }
        }
        try {
            withTimeout(5000) {
                clockEntered.await()
                inputEntered.await()
            }
            assertTrue(manager.signOut() is OperationResult.Success)
            assertEquals(OperationResult.Success(uid), manager.signIn("a@example.com", "validPass"))
            assertTrue(manager.currentSession().sessionGeneration > first.sessionGeneration)
        } finally {
            release.countDown()
        }
        assertFalse(send.await() is OperationResult.Success)
        assertTrue(selection.await().exceptionOrNull() is IOException)
        assertThrows(IOException::class.java) { drafts.save(write, uid, draft) }
        val abandoned = FrozenCapsuleOperation.freeze(message, uid, "UTC", now)
        val replacement = runCatching { PrivatePublicationDrafts(photos, drafts).replacement(write, abandoned, "new") }
        assertTrue(replacement.exceptionOrNull() is IOException)
        assertFalse(root.walk().any { it.isFile })
    }

    /** Recreates the manager at the gap between invalidation and SDK sign-out. */
    @Test fun interruptedLogoutRecoversFromDiskBeforeSameUidLogin() = runBlocking {
        val root = temporaryFolder.newFolder()
        val retained = File(root, "retained")
        val uid = "user-123"
        val snapshot = SessionSnapshot(uid, 0L)
        sessionTracker.startSession(uid)
        fakeAuthAdapter.uid = uid
        val store = DefaultLocalRetentionAdapter(retained, sessionTracker::currentSession)
        assertTrue(store.saveRetainedContent(sessionTracker.currentSession(), "one", Instant.now(),
            "private", null, null) is OperationResult.Success)
        assertTrue(store.saveRetainedContent(snapshot, "zero", Instant.now(), "old", null, null)
            is OperationResult.Denied)
        val cleanup = AccountCleanupOrchestrator(store, noBackupDir = root)
        val interrupted = object : SessionTracker by sessionTracker {
            override fun clearSession() {
                assertEquals(uid, cleanup.getPendingCleanupUid())
                sessionTracker.clearSession()
                throw CancellationException("Interrupt before SDK sign-out")
            }
        }
        val manager = cleanup.sessionManager(fakeAuthAdapter, fakeFirestoreAdapter, interrupted)
        assertThrows(CancellationException::class.java) {
            runBlocking { manager.signOut() }
        }
        assertEquals(uid, fakeAuthAdapter.activeUid())
        assertTrue(retained.walk().any { it.name == "content.text" })
        val restarted = DefaultSessionTracker()
        val disk = AccountCleanupOrchestrator(DefaultLocalRetentionAdapter(retained, restarted::currentSession),
            noBackupDir = root)
        val recovered = disk.sessionManager(fakeAuthAdapter, fakeFirestoreAdapter, restarted)
        assertNull(recovered.restoreSessionIfAuthenticated())
        assertNull(fakeAuthAdapter.activeUid())
        assertFalse(retained.walk().any { it.isFile })
        assertFalse(disk.hasPendingCleanup())
        assertEquals(OperationResult.Success(uid), recovered.signIn("user@example.com", "validPass"))
        assertEquals(uid, restarted.currentSession().uid)
    }

    /** A server acknowledgement from an old generation cannot replace the opaque recovery record. */
    @Test fun delayedPublicationAcknowledgementPreservesCompactedRecord() = runBlocking {
        val root = temporaryFolder.newFolder()
        val ops = FrozenOperationJournal(File(root, "operations"))
        val cleanup = AccountCleanupOrchestrator(
            DefaultLocalRetentionAdapter(File(root, "retained"), sessionTracker::currentSession),
            journal = ops, noBackupDir = root)
        val manager = cleanup.sessionManager(fakeAuthAdapter, fakeFirestoreAdapter, sessionTracker)
        assertTrue(manager.signIn("a@example.com", "validPass") is OperationResult.Success)
        val first = manager.currentSession()
        val entered = CompletableDeferred<Unit>()
        val ack = CompletableDeferred<Unit>()
        val now = Instant.parse("2026-11-26T10:00:00Z")
        val coordinator = CapsulePublicationCoordinator(ops, CapsulePublicationBackend {
            entered.complete(Unit)
            ack.await()
        }, null, manager::currentSession, now = { now })
        val draft = CapsuleDraft("one", "recipient", "private", null,
            CapsuleLock(LockMode.TIME, now.plusSeconds(3600)), isRecipientConfirmed = true)
        val send = async { coordinator.publish(draft, first) }
        try {
            withTimeout(5000) { entered.await() }
            assertTrue(manager.signOut() is OperationResult.Success)
            assertTrue(manager.signIn("a@example.com", "validPass") is OperationResult.Success)
        } finally {
            ack.complete(Unit)
        }
        assertTrue(send.await() is OperationResult.Denied)
        val record = requireNotNull(ops.load("user-123", "one"))
        assertTrue(record.operation.isOpaque)
        assertEquals("", record.operation.text)
        assertEquals(PublicationPhase.PUBLISHING, record.phase)
    }

    /** Sign-in and sign-up keep the former UID on disk while the SDK request is outstanding. */
    @Test fun formerAccountMarkerSpansAuthenticationRequest() = runBlocking {
        for (register in listOf(false, true)) {
            val root = temporaryFolder.newFolder()
            val tracker = DefaultSessionTracker()
            tracker.startSession("former")
            fakeAuthAdapter.uid = if (register) null else "former"
            val disk = AccountCleanupOrchestrator(DefaultLocalRetentionAdapter(File(root, "retained"),
                tracker::currentSession), noBackupDir = root)
            val auth = object : AuthAdapter by fakeAuthAdapter {
                override suspend fun signIn(email: String, password: String): OperationResult<String> {
                    assertEquals("former", disk.getPendingCleanupUid())
                    assertFalse(tracker.currentSession().isAuthenticated)
                    return fakeAuthAdapter.signIn(email, password)
                }
                override suspend fun signUp(email: String, password: String, displayName: String): OperationResult<String> {
                    assertEquals("former", disk.getPendingCleanupUid())
                    assertFalse(tracker.currentSession().isAuthenticated)
                    return fakeAuthAdapter.signUp(email, password, displayName)
                }
            }
            val manager = disk.sessionManager(auth, fakeFirestoreAdapter, tracker)
            val result = if (register) manager.signUpAndCreateProfile("a@example.com", "validPass", "Name")
                else manager.signIn("a@example.com", "validPass")
            assertTrue(result is OperationResult.Success)
            assertFalse(disk.hasPendingCleanup())
        }
    }

    /** A still-active SDK credential keeps its cleanup obligation until a later recovery. */
    @Test fun failedSdkSignOutLeavesMarkerForRestoration() = runBlocking {
        val root = temporaryFolder.newFolder()
        val uid = "user-a"
        val disk = AccountCleanupOrchestrator(DefaultLocalRetentionAdapter(root, sessionTracker::currentSession),
            noBackupDir = File(root, "markers"))
        fakeAuthAdapter.uid = uid
        sessionTracker.startSession(uid)
        val failing = object : AuthAdapter by fakeAuthAdapter {
            override fun signOut() = Unit
        }
        val manager = disk.sessionManager(failing, fakeFirestoreAdapter, sessionTracker)
        assertTrue(manager.signOut() is OperationResult.Unavailable)
        assertEquals(uid, disk.getPendingCleanupUid())
        assertFalse(manager.currentSession().isAuthenticated)
        val recovered = disk.sessionManager(fakeAuthAdapter, fakeFirestoreAdapter, DefaultSessionTracker())
        assertNull(recovered.restoreSessionIfAuthenticated())
        assertNull(fakeAuthAdapter.activeUid())
        assertFalse(disk.hasPendingCleanup())
    }

    /** Restoring B drains an outstanding obligation for A without purging B's own retained files. */
    @Test fun restoreDifferentUidRecoversOnlyPendingAccount() = runBlocking {
        val root = temporaryFolder.newFolder()
        val store = DefaultLocalRetentionAdapter(File(root, "retained"), sessionTracker::currentSession)
        val disk = AccountCleanupOrchestrator(store, noBackupDir = root)
        sessionTracker.startSession("A")
        assertTrue(store.saveRetainedContent(sessionTracker.currentSession(), "one", Instant.now(), "A",
            null, null) is OperationResult.Success)
        disk.markCleanupPending("A")
        sessionTracker.startSession("B")
        fakeAuthAdapter.uid = "B"
        assertTrue(store.saveRetainedContent(sessionTracker.currentSession(), "two", Instant.now(), "B",
            null, null) is OperationResult.Success)
        val manager = disk.sessionManager(fakeAuthAdapter, fakeFirestoreAdapter, sessionTracker)
        assertEquals("B", manager.restoreSessionIfAuthenticated())
        assertTrue(store.readRetainedContent(manager.currentSession(), "two") is OperationResult.Success)
        assertEquals(listOf("B"), root.walk().filter { it.name == "content.text" }.map { it.readText() }.toList())
        assertFalse(disk.hasPendingCleanup())
    }

    /** A late SDK callback displacing B records B before invalidation and purges its actual files. */
    @Test fun staleSdkCallbackDurablyRetiresDisplacedAccount() = runBlocking {
        val root = temporaryFolder.newFolder()
        val store = DefaultLocalRetentionAdapter(File(root, "retained"), sessionTracker::currentSession)
        val disk = AccountCleanupOrchestrator(store, noBackupDir = root)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val auth = object : AuthAdapter by fakeAuthAdapter {
            override suspend fun signIn(email: String, password: String): OperationResult<String> {
                entered.complete(Unit)
                release.await()
                return fakeAuthAdapter.signIn(email, password)
            }
        }
        val observer = object : SessionTracker by sessionTracker {
            override fun clearSession() {
                assertEquals("B", disk.getPendingCleanupUid())
                sessionTracker.clearSession()
            }
        }
        val manager = disk.sessionManager(auth, fakeFirestoreAdapter, observer)
        val late = async { manager.signIn("a@example.com", "validPass") }
        withTimeout(5000) { entered.await() }
        try {
            sessionTracker.startSession("B")
            fakeAuthAdapter.uid = "B"
            assertTrue(store.saveRetainedContent(manager.currentSession(), "one", Instant.now(), "B",
                null, null) is OperationResult.Success)
        } finally {
            release.complete(Unit)
        }
        assertTrue(late.await() is OperationResult.Denied)
        assertFalse(root.walk().any { it.isFile })
        assertFalse(manager.currentSession().isAuthenticated)
        assertNull(auth.activeUid())
    }

    /** A real unreadable operation blocks account activation and keeps both cleanup obligations recoverable. */
    @Test fun corruptFormerJournalBlocksReplacementUntilRetry() = runBlocking {
        val root = temporaryFolder.newFolder()
        val journalRoot = File(root, "operations")
        val ops = FrozenOperationJournal(journalRoot)
        val store = DefaultLocalRetentionAdapter(File(root, "retained"), sessionTracker::currentSession)
        val disk = AccountCleanupOrchestrator(store, journal = ops, noBackupDir = root)
        val manager = disk.sessionManager(fakeAuthAdapter, fakeFirestoreAdapter, sessionTracker)
        assertTrue(manager.signIn("a@example.com", "validPass") is OperationResult.Success)
        val first = manager.currentSession()
        val now = Instant.parse("2026-11-26T10:00:00Z")
        val draft = CapsuleDraft("one", "recipient", "private", null,
            CapsuleLock(LockMode.TIME, now.plusSeconds(3600)), isRecipientConfirmed = true)
        val op = FrozenCapsuleOperation.freeze(draft, "user-123", "UTC", now)
        ops.save(StorageSession(first, manager::currentSession), FrozenOperationRecord(op))
        val record = journalRoot.walk().single { it.isFile }
        val saved = record.readBytes()
        record.writeText("version=invalid")
        assertTrue(manager.signIn("userB@example.com", "validPass") is OperationResult.Unavailable)
        assertFalse(manager.currentSession().isAuthenticated)
        assertNull(fakeAuthAdapter.activeUid())
        assertEquals("user-123", disk.getPendingCleanupUid())
        record.writeBytes(saved)
        assertEquals(OperationResult.Success("user-b"), manager.signIn("userB@example.com", "validPass"))
        assertTrue(requireNotNull(ops.load("user-123", "one")).operation.isOpaque)
        assertFalse(disk.hasPendingCleanup())
    }

    /** A failed durable write leaves the current SDK identity and generation available for retry. */
    @Test fun markerWriteFailureStopsReplacementBeforeSdkMutation() = runBlocking {
        sessionTracker.startSession("user-a")
        fakeAuthAdapter.uid = "user-a"
        val original = sessionTracker.currentSession()
        val root = temporaryFolder.newFile()
        val disk = AccountCleanupOrchestrator(DefaultLocalRetentionAdapter(temporaryFolder.newFolder(),
            sessionTracker::currentSession), noBackupDir = root)
        val manager = disk.sessionManager(fakeAuthAdapter, fakeFirestoreAdapter, sessionTracker)
        assertTrue(manager.signIn("userB@example.com", "pass123") is OperationResult.Unavailable)
        assertEquals(original, manager.currentSession())
        assertEquals(original.uid, fakeAuthAdapter.activeUid())
    }

    @Test
    fun testAccountReplacementInvalidatesOldSessionBeforeOnAccountClearedCallback() = runBlocking {
        var oldSessionValidAtCleanup: Boolean? = null
        var clearedUid: String? = null
        var sessionAtCleanup: SessionSnapshot? = null
        var initialSession: SessionSnapshot? = null

        val manager = AccountSessionManager(
            fakeAuthAdapter,
            fakeFirestoreAdapter,
            sessionTracker,
            onAccountCleared = { uid ->
                clearedUid = uid
                sessionAtCleanup = sessionTracker.currentSession()
                initialSession?.let { oldSession ->
                    oldSessionValidAtCleanup = sessionTracker.isSessionValid(oldSession)
                }
                OperationResult.Success(Unit)
            }
        )

        fakeAuthAdapter.uid = "user-old"
        sessionTracker.startSession("user-old")
        initialSession = sessionTracker.currentSession()

        fakeAuthAdapter.uid = "user-new"
        val res = manager.signIn("user-new@example.com", "validPass")

        assertTrue(res is OperationResult.Success)
        assertEquals("user-old", clearedUid)
        // Under DD-18, during onAccountCleared the old session is cleared and unauthenticated BEFORE the new session starts
        assertFalse("Active session must be unauthenticated while old account cleanup runs", sessionAtCleanup?.isAuthenticated == true)
        assertFalse("Old session snapshot must be invalid when onAccountCleared runs", oldSessionValidAtCleanup == true)
        assertEquals("user-123", sessionTracker.currentSession().uid)
    }

    @Test
    fun testSignUpFinishesAfterOriginalSessionInvalidated() = runBlocking {
        val delayedAuth = object : AuthAdapter by fakeAuthAdapter {
            override suspend fun signUp(email: String, password: String, displayName: String): OperationResult<String> {
                delay(50)
                fakeAuthAdapter.uid = "user-signup-stale"
                return OperationResult.Success("user-signup-stale")
            }
        }
        val manager = AccountSessionManager(delayedAuth, fakeFirestoreAdapter, sessionTracker)

        val job = async { manager.signUpAndCreateProfile("signup@example.com", "pass123", "New User") }

        delay(10)
        sessionTracker.clearSession()

        val result = job.await()
        assertTrue("Stale sign-up must be denied", result is OperationResult.Denied)
        assertNull("Auth must be signed out for stale sign-up", fakeAuthAdapter.activeUid())
        assertFalse("SessionTracker must remain unauthenticated", sessionTracker.currentSession().isAuthenticated)
    }

    @Test
    fun testCurrentAccountRemainsConsistentBetweenAuthAndSessionTracker() = runBlocking {
        val res = accountSessionManager.signIn("user@example.com", "validPass")
        assertTrue(res is OperationResult.Success)

        assertEquals(fakeAuthAdapter.activeUid(), sessionTracker.currentSession().uid)
        assertTrue(sessionTracker.currentSession().isAuthenticated)

        accountSessionManager.signOut()
        assertEquals(fakeAuthAdapter.activeUid(), sessionTracker.currentSession().uid)
        assertFalse(sessionTracker.currentSession().isAuthenticated)
    }

    @Test
    fun testSignOutClearsSessionTrackerWithoutDuplicateCalls() {
        runBlocking {
            accountSessionManager.signIn("user@example.com", "pass123")
            val initialGen = sessionTracker.currentSession().sessionGeneration

            accountSessionManager.signOut()

            assertFalse(sessionTracker.currentSession().isAuthenticated)
            assertEquals(initialGen + 1, sessionTracker.currentSession().sessionGeneration)
        }
    }
}
