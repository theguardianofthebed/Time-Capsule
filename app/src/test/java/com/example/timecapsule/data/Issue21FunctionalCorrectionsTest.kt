package com.example.timecapsule.data

import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.example.timecapsule.MainActivity
import com.example.timecapsule.R
import com.example.timecapsule.TimeCapsuleApplication
import com.example.timecapsule.data.AccountSessionManager
import com.example.timecapsule.data.adapter.AuthAdapter
import com.example.timecapsule.data.adapter.DefaultFirestoreAdapter
import com.example.timecapsule.data.adapter.DefaultLocalRetentionAdapter
import com.example.timecapsule.data.adapter.DefaultLocationAdapter
import com.example.timecapsule.data.adapter.DefaultStorageAdapter
import com.example.timecapsule.data.adapter.FirestoreAdapter
import com.example.timecapsule.data.adapter.LocalRetentionAdapter
import com.example.timecapsule.data.adapter.StorageAdapter
import com.example.timecapsule.data.di.AppDependencyContainer
import com.example.timecapsule.data.recovery.ForegroundRecoveryCoordinator
import com.example.timecapsule.data.session.DefaultSessionTracker
import com.example.timecapsule.domain.model.Availability
import com.example.timecapsule.domain.model.CapsuleDraft
import com.example.timecapsule.domain.model.CapsuleListsSnapshot
import com.example.timecapsule.domain.model.CapsuleLock
import com.example.timecapsule.domain.model.CapsuleSummary
import com.example.timecapsule.domain.model.ForegroundLocationSample
import com.example.timecapsule.domain.model.ListProvenance
import com.example.timecapsule.domain.model.LocationTarget
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.domain.model.OriginalPhotoFormat
import com.example.timecapsule.domain.model.ProtectedCapsuleContent
import com.example.timecapsule.domain.model.PublicationPhase
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.model.RecipientUnlockRequirements
import com.example.timecapsule.domain.model.RecoveredPublication
import com.example.timecapsule.domain.model.SendStatus
import com.example.timecapsule.domain.model.UnlockConfirmationKind
import com.example.timecapsule.domain.model.UnlockVerificationOutcome
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot
import com.example.timecapsule.ui.s03.S03CapsuleListViewModel
import com.example.timecapsule.ui.s07.DetailStatus
import com.example.timecapsule.ui.s07.S07CapsuleDetailViewModel
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageReference
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow

/**
 * Mandatory regression test suite for Issue #21 functional corrections.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class Issue21FunctionalCorrectionsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var rootDir: File
    private lateinit var sessionTracker: DefaultSessionTracker
    private lateinit var fakeAuthAdapter: FakeAuthAdapter
    private lateinit var fakeFirestoreAdapter: FakeFirestoreAdapter
    private lateinit var retentionAdapter: DefaultLocalRetentionAdapter
    private lateinit var repository: DefaultCapsuleRepository

    private val userA = "user-a"
    private val userB = "user-b"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        rootDir = tempFolder.newFolder("retained-content")
        sessionTracker = DefaultSessionTracker()
        sessionTracker.startSession(userA)
        fakeAuthAdapter = FakeAuthAdapter(userA)
        fakeFirestoreAdapter = FakeFirestoreAdapter()
        retentionAdapter = DefaultLocalRetentionAdapter(
            root = rootDir,
            session = { sessionTracker.currentSession() }
        )
        repository = DefaultCapsuleRepository(
            sessionTracker = sessionTracker,
            authAdapter = fakeAuthAdapter,
            firestoreAdapter = fakeFirestoreAdapter,
            retentionAdapter = retentionAdapter
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A permitted server read can confirm the protected document is missing. */
    @Test
    @Config(shadows = [MissingDocumentReference::class, MissingDocument::class])
    fun detailMissingDocumentIsAnAssetFailure() = runBlocking {
        val adapter = DefaultFirestoreAdapter(contextSupplier = { RuntimeEnvironment.getApplication() })
        val result = adapter.readProtectedContent("missing-document", userA)
        assertTrue("Confirmed absence was returned as $result", result is OperationResult.Invalid)
    }

    /** Replace only the server read with a successful SDK task for a missing document. */
    @Implements(value = DocumentReference::class, isInAndroidSdk = false)
    class MissingDocumentReference {
        @Implementation fun get(source: Source) =
            Tasks.forResult<DocumentSnapshot>(Shadow.newInstanceOf(DocumentSnapshot::class.java))
    }

    /** The server answered the read and reported no document at the requested path. */
    @Implements(value = DocumentSnapshot::class, isInAndroidSdk = false)
    class MissingDocument {
        @Implementation fun exists() = false
    }

    /** Pass an SDK permission failure through the production Storage adapter. */
    @Test
    @Config(shadows = [DeniedStorage::class, DeniedReference::class])
    fun retentionStoragePermissionFailureRemainsDenied() = runBlocking {
        val adapter = DefaultStorageAdapter(Shadow.newInstanceOf(FirebaseStorage::class.java),
            FirebaseFirestore.getInstance(), FirebaseAuth.getInstance())
        DeniedReference.reads = 0
        val result = adapter.downloadPhotoBytes("capsuleMedia/sender/id/original", 1024)
        assertEquals(1, DeniedReference.reads)
        assertTrue("Storage 403 was returned as $result", result is OperationResult.Denied)
    }

    /** Return a reference without opening an external Storage connection. */
    @Implements(value = FirebaseStorage::class, isInAndroidSdk = false)
    class DeniedStorage {
        @Implementation fun getReference(): StorageReference = Shadow.newInstanceOf(StorageReference::class.java)
    }

    /** Supply the SDK's actual 403 exception when the adapter awaits getBytes. */
    @Implements(value = StorageReference::class, isInAndroidSdk = false)
    class DeniedReference {
        companion object { var reads = 0 }
        @Implementation fun child(path: String): StorageReference = Shadow.newInstanceOf(StorageReference::class.java)
        @Implementation fun getBytes(maxDownloadSizeBytes: Long): Task<ByteArray> {
            reads++
            return Tasks.forException(StorageException.fromExceptionAndHttpCode(IOException("Forbidden"), 403)!!)
        }
    }

    /** Rewrite an actual private marker to reproduce on-disk legacy formats. */
    private fun marker(id: String) = File(File(rootDir, sha256(userA.toByteArray())),
        "$id/verified-marker.properties")

    /** Load the exact manifest that will be consumed after a simulated restart. */
    private fun properties(file: File) = Properties().apply { file.inputStream().use { load(it) } }

    /** Test complete text alongside each broken identity, timestamp, flag and required file. */
    @Test fun retentionLegacyMigrationValidatesOriginalRecord() = runBlocking {
        val session = sessionTracker.currentSession()
        val now = Instant.now()
        for (version in listOf("1", "2")) {
            for (variant in listOf("complete", "uid", "capsuleId", "verifiedUnlockedAt", "hasPhoto", "missing-text")) {
                val id = "legacy-$version-$variant"
                retentionAdapter.saveRetainedContent(session, id, now, "original", null, null)
                val file = marker(id)
                val old = properties(file).apply {
                    setProperty("version", version)
                    remove("contentCommitted")
                    when (variant) {
                        "complete" -> Unit
                        "missing-text" -> File(file.parentFile, "content.text").delete()
                        else -> setProperty(variant, "invalid")
                    }
                }
                file.outputStream().use { old.store(it, null) }
                retentionAdapter.saveVerifiedUnlockMarker(session, id, now)
                val migrated = properties(file)
                assertEquals("3", migrated.getProperty("version"))
                assertEquals(id, (variant == "complete").toString(), migrated.getProperty("contentCommitted"))
                val read = retentionAdapter.readRetainedContent(session, id)
                assertEquals(id, variant == "complete", read is OperationResult.Success)
            }
        }
    }

    /** Remote metadata cannot repair a malformed committed local manifest. */
    @Test fun retentionMalformedCommittedFlagCannotAdoptRemoteDefault() = runBlocking {
        val session = sessionTracker.currentSession()
        val id = "invalid-flag"
        retentionAdapter.saveRetainedContent(session, id, Instant.now(), "old bytes", null, null)
        val file = marker(id)
        val broken = properties(file).apply { setProperty("hasText", "unknown") }
        file.outputStream().use { broken.store(it, null) }
        retentionAdapter.saveVerifiedUnlockMarker(session, id, Instant.now(), hasText = true)
        assertEquals("false", properties(file).getProperty("contentCommitted"))
        assertTrue(retentionAdapter.readRetainedContent(session, id) !is OperationResult.Success)
    }

    /** Set up a recipient with previously verified, server-authorized combined-lock metadata. */
    private fun seedRecipient(id: String): CapsuleSummary {
        val now = Instant.now()
        val lock = CapsuleLock(LockMode.TIME_AND_LOCATION, now.minusSeconds(10), LocationTarget(37.4, 127.1))
        val summary = CapsuleSummary(id, userB, userA, lock, SendStatus.SENT, Availability.AVAILABLE,
            creationZone = "Asia/Seoul", unlockedAt = now)
        fakeFirestoreAdapter.summaries[id] = summary
        fakeFirestoreAdapter.requirements[id] = RecipientUnlockRequirements(id, userA, true, lock.mode,
            lock.unlockAt, lock.location, now)
        fakeFirestoreAdapter.protectedContent[id] = ProtectedCapsuleContent(id, "retained text", null)
        return summary
    }

    /** Reopen through the list and detail repository after fully persisting remote content. */
    @Test fun retentionOfflineDetailPreservesParticipantsZoneAndLock() = runBlocking {
        val original = seedRecipient("complete-metadata")
        assertTrue(repository.readContent(original.capsuleId) is OperationResult.Success)
        fakeFirestoreAdapter.isNetworkDown = true
        val detail = (repository.getSummary(original.capsuleId) as OperationResult.Success).data
        val row = (repository.lists(userA) as OperationResult.Success).data.received.single()
        for (item in listOf(detail, row)) {
            assertEquals(original.senderUid, item.senderUid)
            assertEquals(original.recipientUid, item.recipientUid)
            assertEquals(original.creationZone, item.creationZone)
            assertEquals(original.lock, item.lock)
        }
    }

    /** A marker that predates lock metadata must not manufacture a time lock. */
    @Test fun retentionUnknownLegacyLockStaysUnknown() = runBlocking {
        val session = sessionTracker.currentSession()
        retentionAdapter.saveRetainedContent(session, "unknown-lock", Instant.now(), "text", null, null)
        fakeFirestoreAdapter.isNetworkDown = true
        val detail = (repository.getSummary("unknown-lock") as OperationResult.Success).data
        assertEquals(null, detail.lock)
    }

    /** Only download unavailability permits fallback to verified bytes for the current recipient. */
    @Test fun retentionUnavailableDownloadUsesCommittedBytes() = runBlocking {
        val id = seedRecipient("previously-saved").capsuleId
        assertTrue(repository.readContent(id) is OperationResult.Success)
        fakeFirestoreAdapter.contentFailure = OperationResult.Unavailable("Download interrupted")
        val read = repository.readContent(id)
        assertTrue(read is OperationResult.Success)
        val content = (read as OperationResult.Success).data
        assertTrue(content.availableOffline)
        assertEquals("retained text", content.text)
    }

    /** An authoritative content error survives intact even when a local copy exists. */
    @Test fun retentionAuthoritativeContentErrorsRemainAuthoritative() = runBlocking {
        val id = seedRecipient("revoked").capsuleId
        assertTrue(repository.readContent(id) is OperationResult.Success)
        for (result in listOf(OperationResult.Denied("Revoked"), OperationResult.Invalid("Malformed"))) {
            fakeFirestoreAdapter.contentFailure = result
            assertEquals(result, repository.readContent(id))
        }
    }

    /** Feed an actual S07 Fragment a deterministic verification or content result. */
    private fun detailBackend(
        available: Boolean = false,
        verification: OperationResult<UnlockVerificationOutcome> = OperationResult.Unavailable("Location disabled"),
        body: OperationResult<ProtectedCapsuleContent> = OperationResult.Unavailable("Missing asset")
    ): CapsuleRepository = object : CapsuleRepository by repository {
        override suspend fun getSummary(capsuleId: String, targetSession: SessionSnapshot) =
            OperationResult.Success(CapsuleSummary(capsuleId, userB, userA, CapsuleLock(LockMode.LOCATION),
                SendStatus.SENT, if (available) Availability.AVAILABLE else Availability.LOCKED))
        override suspend fun verifyUnlockOutcome(capsuleId: String, targetSession: SessionSnapshot) = verification
        override suspend fun readContent(capsuleId: String, targetSession: SessionSnapshot) = body
    }

    /** Register and repair share a single claim, even if both methods are called in the same event loop. */
    @Test fun duplicateRegisterOrRepairDoesNotWriteProfileTwice() {
        fakeAuthAdapter.signOut()
        sessionTracker.clearSession()
        val activity = openScreen(repository)
        val model = com.example.timecapsule.ui.s02.S02RegisterViewModel(activity.container.accountSessionManager)
        model.registerAndCreateProfile("reader@example.com", "example", "Reader")
        model.registerAndCreateProfile("reader@example.com", "example", "Reader")
        settleScreen()
        assertEquals(1, fakeAuthAdapter.signUpCalls)
        assertEquals(1, fakeFirestoreAdapter.profileWrites)
        model.repairProfile("Reader")
        model.repairProfile("Reader")
        settleScreen()
        assertEquals(2, fakeFirestoreAdapter.profileWrites)
    }

    /** Partial registration repairs the existing UID and never sends a second sign-up request. */
    @Test fun registrationFailureRepairsSameAccount() {
        fakeAuthAdapter.signOut()
        sessionTracker.clearSession()
        fakeFirestoreAdapter.profileResult = OperationResult.Invalid("Missing")
        fakeFirestoreAdapter.failProfileWrite = true
        val activity = openScreen(repository)
        activity.findViewById<View>(R.id.btnGoToRegister).performClick()
        settleScreen()
        activity.findViewById<TextView>(R.id.etEmail).text = "reader@example.com"
        activity.findViewById<TextView>(R.id.etPassword).text = "example"
        activity.findViewById<TextView>(R.id.etConfirmPassword).text = "example"
        activity.findViewById<TextView>(R.id.etDisplayName).text = "Reader"
        activity.findViewById<Button>(R.id.btnRegister).performClick()
        settleScreen()
        assertEquals(activity.getString(R.string.s02_headline_repair), activity.findViewById<TextView>(R.id.tvS02Title).text)
        fakeFirestoreAdapter.failProfileWrite = false
        activity.findViewById<Button>(R.id.btnRegister).performClick()
        settleScreen()
        assertNotNull(activity.findViewById<View>(R.id.btnTabReceived))
        assertEquals(1, fakeAuthAdapter.signUpCalls)
        assertEquals("new-reader", sessionTracker.currentSession().uid)
    }

    /** While a sign-in request is suspended, recreation restores progress and suppresses a second submission. */
    @Test fun signInRotationKeepsOperation() {
        fakeAuthAdapter.signOut()
        sessionTracker.clearSession()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        fakeAuthAdapter.signInGate = gate
        openScreen(repository)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        settleScreen()
        val activity = controller.get()
        activity.findViewById<TextView>(R.id.etEmail).text = "reader@example.com"
        activity.findViewById<TextView>(R.id.etPassword).text = "example"
        activity.findViewById<Button>(R.id.btnSignIn).performClick()
        settleScreen()
        controller.recreate()
        settleScreen()
        val rotated = controller.get()
        assertEquals(View.VISIBLE, rotated.findViewById<View>(R.id.pbLoading).visibility)
        assertFalse(rotated.findViewById<Button>(R.id.btnSignIn).isEnabled)
        assertFalse(rotated.findViewById<View>(R.id.btnGoToRegister).isEnabled)
        assertEquals("", rotated.findViewById<TextView>(R.id.etPassword).text.toString())
        gate.complete(Unit)
        settleScreen()
        assertEquals(1, fakeAuthAdapter.signInCalls)
        assertNotNull(rotated.findViewById<View>(R.id.btnTabReceived))
    }

    /** A restored Firebase account still needs a directory profile to enter the list. */
    @Test fun missingProfileOpensRepairOnStartup() {
        fakeFirestoreAdapter.profileResult = OperationResult.Invalid("Recipient not found")
        val activity = openScreen(repository)
        assertEquals(activity.getString(R.string.s02_headline_repair),
            activity.findViewById<TextView>(R.id.tvS02Title)?.text)
    }

    /** An old repair form cannot write a display name into a later session of the same UID. */
    @Test fun staleRepairScreenCannotWriteProfile() {
        fakeFirestoreAdapter.profileResult = OperationResult.Invalid("Missing")
        val activity = openScreen(repository)
        val submit = activity.findViewById<Button>(R.id.btnRegister)
        activity.findViewById<TextView>(R.id.etDisplayName).text = "Old form"
        sessionTracker.startSession(userA)
        submit.performClick()
        settleScreen()
        assertEquals(0, fakeFirestoreAdapter.profileWrites)
    }

    /** A successful Auth callback from an earlier generation cannot start a new directory write. */
    @Test fun interruptedRepairCannotStartDirectoryWrite() {
        fakeFirestoreAdapter.profileResult = OperationResult.Invalid("Missing")
        val activity = openScreen(repository)
        fakeAuthAdapter.afterNameUpdate = { sessionTracker.startSession(userA) }
        activity.findViewById<Button>(R.id.btnRegister).performClick()
        settleScreen()
        assertEquals(0, fakeFirestoreAdapter.profileWrites)
    }

    /** Failed verification offers a read retry without creating another account or overwriting a profile. */
    @Test fun unavailableProfileOffersReadRetry() {
        fakeFirestoreAdapter.profileResult = OperationResult.Unavailable("offline")
        val activity = openScreen(repository)
        val action = activity.findViewById<Button>(R.id.btnRegister)
        assertEquals("Check profile again", action?.text)
        fakeFirestoreAdapter.profileResult = null
        action.performClick()
        settleScreen()
        assertNotNull(activity.findViewById<View>(R.id.btnTabReceived))
    }

    /** System Back follows the same draft steps as the visible controls. */
    @Test fun systemBackReturnsFromLockToDraft() {
        val activity = openScreen(withEmptyJournal())
        activity.navigateToContentDraft()
        settleScreen()
        activity.findViewById<TextView>(R.id.etCapsuleTitle).text = "Draft title"
        activity.navigateToRecipientLock()
        settleScreen()
        activity.onBackPressedDispatcher.onBackPressed()
        settleScreen()
        assertEquals("Draft title", activity.findViewById<TextView>(R.id.etCapsuleTitle)?.text?.toString())
    }

    /** Accessibility selection survives a trip from Sent to detail and back. */
    @Test fun detailBackRestoresSelectedSentTab() {
        val activity = openScreen(repository)
        activity.findViewById<Button>(R.id.btnTabSent).performClick()
        settleScreen()
        activity.navigateToCapsuleDetail("detail-id")
        settleScreen()
        activity.findViewById<View>(R.id.btnS07Back).performClick()
        settleScreen()
        assertTrue(activity.findViewById<Button>(R.id.btnTabSent).isSelected)
    }

    /** Unknown availability must never be announced as a confirmed lock. */
    @Test fun unknownReceivedAvailabilityRequiresVerification() {
        fakeFirestoreAdapter.receivedList[userA] = listOf(CapsuleSummary("id", userB, userA, null,
            SendStatus.SENT, Availability.UNKNOWN))
        val activity = openScreen(repository)
        assertEquals("Verification required", activity.findViewById<TextView>(R.id.tvItemStatusBadge)?.text)
    }

    /** The first permission request may open a dialog. A suppressed repeat opens notification settings. */
    @Test fun notificationDenialHasSettingsRoute() {
        val activity = openScreen(repository)
        activity.navigateToAccountSettings()
        settleScreen()
        val action = activity.findViewById<View>(R.id.btnOpenNotificationSettings)
        action.performClick()
        settleScreen()
        action.performClick()
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, shadowOf(activity).nextStartedActivity?.action)
    }

    /** Test back-to-back programmatic submissions before the first coroutine can run. */
    @Test fun duplicateAuthSubmission() {
        fakeAuthAdapter.signOut()
        sessionTracker.clearSession()
        val activity = openScreen(repository)
        val model = com.example.timecapsule.ui.s01.S01SignInViewModel(activity.container.accountSessionManager)
        model.signIn("reader@example.com", "secret")
        model.signIn("reader@example.com", "secret")
        settleScreen()
        assertEquals(1, fakeAuthAdapter.signInCalls)
    }

    /** A queued render for the same capsule from an expired generation must clear its body immediately. */
    @Test fun staleDetailRenderClearsBody() {
        val activity = openDetail(detailBackend(true, body = OperationResult.Success(
            ProtectedCapsuleContent("detail-id", "old caption", null, null))))
        val frag = activity.supportFragmentManager.findFragmentById(R.id.fragmentContainer)!!
        val model = androidx.lifecycle.ViewModelProvider(frag)[S07CapsuleDetailViewModel::class.java]
        val oldState = model.uiState.value
        val body = activity.findViewById<TextView>(R.id.tvS07BodyText)
        assertEquals("old caption", body.text.toString())
        sessionTracker.startSession(userA)
        frag.javaClass.getDeclaredMethod("renderUiState", oldState.javaClass).apply {
            isAccessible = true
            invoke(frag, oldState)
        }
        assertEquals("", body.text.toString())
    }

    /** The actual confirmation fragment renders operation evidence independently of its draft fields. */
    @Test fun publicationControlsFollowEvidence() {
        val activity = openScreen(withEmptyJournal())
        activity.navigateToConfirmSend()
        settleScreen()
        val model = androidx.lifecycle.ViewModelProvider(activity)[com.example.timecapsule.ui.draft.CapsuleDraftViewModel::class.java]
        @Suppress("UNCHECKED_CAST")
        val updates = model.javaClass.getDeclaredField("_uiState").apply { isAccessible = true }.get(model)
            as kotlinx.coroutines.flow.MutableStateFlow<com.example.timecapsule.ui.draft.CapsuleDraftUiState>
        updates.value = updates.value.copy(resolvedProfile = null, recipientUidInput = "unknown-recipient")
        settleScreen()
        assertEquals("Recipient unconfirmed", activity.findViewById<TextView>(R.id.tvReviewRecipientName).text)
        updates.value = updates.value.copy(publicationPhase = PublicationPhase.UPLOADING, publicationBusy = true)
        settleScreen()
        val primary = activity.findViewById<Button>(R.id.btnSendCapsule)
        assertFalse(primary.isEnabled)
        assertFalse(primary.isShown && primary.text == activity.getString(R.string.publication_retry))
        updates.value = updates.value.copy(publicationPhase = PublicationPhase.SENT, publicationBusy = false)
        settleScreen()
        assertEquals("View Sent", primary.text)
        primary.performClick()
        settleScreen()
        assertTrue(activity.findViewById<Button>(R.id.btnTabSent).isSelected)
    }

    /** System bars and IME share one inset consumer and repeated dispatch must not accumulate padding. */
    @Test fun rootAppliesInsetsOnce() {
        val root = openScreen(repository).findViewById<View>(R.id.mainRootView)
        val insets = android.view.WindowInsets.Builder()
            .setInsets(android.view.WindowInsets.Type.systemBars(), android.graphics.Insets.of(0, 27, 0, 18))
            .setInsets(android.view.WindowInsets.Type.ime(), android.graphics.Insets.of(0, 0, 0, 120))
            .build()
        repeat(2) { root.dispatchApplyWindowInsets(insets) }
        assertEquals(27, root.paddingTop)
        assertEquals(120, root.paddingBottom)
    }

    /** Compose tests need a confirmed empty journal before the shared ViewModel can enable editing. */
    private fun withEmptyJournal(): CapsuleRepository = object : CapsuleRepository by repository {
        override suspend fun recoverPublications(targetSession: SessionSnapshot) =
            OperationResult.Success(emptyList<RecoveredPublication>())
    }

    /** A network failure during first verification cannot determine which condition is pending. */
    @Test fun offlineFirstVerificationKeepsContentHidden() {
        val activity = openDetail(detailBackend(verification = OperationResult.Unavailable("Network unavailable")))
        val frag = activity.supportFragmentManager.findFragmentById(R.id.fragmentContainer)!!
        val model = androidx.lifecycle.ViewModelProvider(frag)[S07CapsuleDetailViewModel::class.java]
        assertEquals(DetailStatus.LOCKED_VERIFICATION_FAILED, model.uiState.value.status)
        assertFalse(activity.findViewById<View>(R.id.llS07ContentCard).isShown)
    }

    /** Offline directory lookup must still lead to the account-scoped retained list. */
    @Test fun offlineProfileCanOpenRetainedListWithoutCreating() {
        fakeFirestoreAdapter.profileResult = OperationResult.Unavailable("offline")
        val activity = openScreen(repository)
        assertTrue(activity.findViewById<View>(R.id.tvGoToSignIn).isShown)
        activity.findViewById<View>(R.id.tvGoToSignIn).performClick()
        settleScreen()
        assertNotNull(activity.findViewById<View>(R.id.btnTabReceived))
        activity.findViewById<View>(R.id.btnNavCreate).performClick()
        settleScreen()
        assertEquals("Check profile again", activity.findViewById<Button>(R.id.btnRegister)?.text)
    }

    /** SDK exception diagnostics must not reach an account-screen error label. */
    @Test fun unknownAuthErrorHasActionableCopy() {
        val adapter = com.example.timecapsule.data.adapter.DefaultAuthAdapter()
        val method = adapter.javaClass.getDeclaredMethod("mapAuthException", Exception::class.java)
        method.isAccessible = true
        val result = method.invoke(adapter, IllegalStateException("private provider diagnostic"))
        assertEquals(OperationResult.Unavailable("Account service unavailable. Check your connection and retry."), result)
    }

    /** Even a non-cancellable decoder must not repaint after authorization has been revoked. */
    @Test fun delayedPhotoCannotRepaintAfterRevocation() {
        var revoked = false
        val readable = detailBackend(true)
        val backend = object : CapsuleRepository by readable {
            override suspend fun readContent(capsuleId: String, targetSession: SessionSnapshot): OperationResult<ProtectedCapsuleContent> =
                if (revoked) OperationResult.Denied("Access revoked")
                else OperationResult.Success(ProtectedCapsuleContent(capsuleId, "caption", "original", byteArrayOf(1)))
        }
        val activity = openDetail(backend)
        val screen = activity.supportFragmentManager.findFragmentById(R.id.fragmentContainer)
            as com.example.timecapsule.ui.s07.S07CapsuleDetailFragment
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        screen.decodePhoto = {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                started.complete(Unit)
                release.await()
                android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)
            }
        }
        screen.refreshScreen()
        settleScreen()
        assertTrue(started.isCompleted)
        revoked = true
        screen.refreshScreen()
        settleScreen()
        release.complete(Unit)
        settleScreen()
        assertFalse(activity.findViewById<View>(R.id.llS07ContentCard).isShown)
        assertNull(activity.findViewById<android.widget.ImageView>(R.id.ivS07Photo).drawable)
        assertEquals("", activity.findViewById<TextView>(R.id.tvS07BodyText).text.toString())
    }

    /** Show a capsule through the production activity navigation and fragment lifecycle. */
    private fun openDetail(backend: CapsuleRepository): MainActivity = openScreen(backend).also {
        it.navigateToCapsuleDetail("detail-id")
        settleScreen()
    }

    /** Precise permission failures remain readable on the real screen with a permission action. */
    @Test fun detailScreenPermissionFailurePersists() {
        val message = "Precise location permission is required for location unlock."
        val activity = openDetail(detailBackend(verification = OperationResult.Denied(message)))
        val notice = activity.findViewById<TextView>(R.id.tvS07Banner)
        assertEquals(View.VISIBLE, notice.visibility)
        assertTrue(notice.text.contains(message))
        val action = activity.findViewById<Button>(R.id.btnS07Retry)
        assertTrue(action.text.contains("retry", ignoreCase = true) || action.text.contains("permission", ignoreCase = true))
    }

    /** A disabled provider offers a working Android location settings action. */
    @Test fun detailScreenOpensLocationSettings() {
        val activity = openDetail(detailBackend())
        activity.findViewById<Button>(R.id.btnS07Retry).performClick()
        assertEquals(Settings.ACTION_LOCATION_SOURCE_SETTINGS, shadowOf(activity).nextStartedActivity?.action)
    }

    /** A failed image decode keeps the verified recipient badge and an actionable message. */
    @Test fun detailScreenDecodeFailureKeepsUnlockAndRetry() {
        val bytes = byteArrayOf(1, 2, 3)
        val activity = openDetail(detailBackend(true, body = OperationResult.Success(
            ProtectedCapsuleContent("detail-id", "caption", "original", bytes))))
        val notice = activity.findViewById<TextView>(R.id.tvS07Banner)
        val deadline = System.nanoTime() + 2_000_000_000L
        while (!notice.text.contains("photo", ignoreCase = true) && System.nanoTime() < deadline) {
            settleScreen()
            Thread.sleep(10)
        }
        assertTrue(notice.text.contains("photo", ignoreCase = true))
        assertTrue(activity.findViewById<Button>(R.id.btnS07Retry).isShown)
        assertEquals(activity.getString(R.string.s07_badge_opened),
            activity.findViewById<TextView>(R.id.tvS07StatusBadge).text)
        assertEquals("caption", activity.findViewById<TextView>(R.id.tvS07BodyText).text.toString())
    }

    /** Authentication restoration retries the originally requested ID under the restored snapshot. */
    @Test fun detailWaitForRestorationRetainsIdentity() {
        sessionTracker.clearSession()
        val model = S07CapsuleDetailViewModel(sessionTracker, detailBackend())
        model.loadDetail("detail-id")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("detail-id", model.uiState.value.capsuleId)
        assertEquals(sessionTracker.currentSession(), model.uiState.value.sessionSnapshot)
        sessionTracker.startSession(userA)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("detail-id", model.uiState.value.summary?.capsuleId)
        assertEquals(sessionTracker.currentSession(), model.uiState.value.sessionSnapshot)
    }

    /** Revoked access must not render the missing-file state for an opened capsule. */
    @Test fun detailDeniedContentDiffersFromMissingAsset() {
        val model = S07CapsuleDetailViewModel(sessionTracker,
            detailBackend(true, body = OperationResult.Denied("Access revoked")))
        model.loadDetail("detail-id")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(DetailStatus.ACCESS_DENIED, model.uiState.value.status)
        assertFalse(model.uiState.value.isAssetUnavailable)
        assertEquals(null, model.uiState.value.content)
    }

    /** A confirmed empty Sent query remains distinguishable from an unavailable Received query. */
    @Test fun tabsEmptySentAndUnavailableReceived() {
        fakeFirestoreAdapter.receivedUnavailable = true
        val remoteOnly = DefaultCapsuleRepository(sessionTracker, fakeAuthAdapter, fakeFirestoreAdapter)
        val model = S03CapsuleListViewModel(sessionTracker, remoteOnly)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(model.uiState.value.isSentUnavailable)
        assertTrue(model.uiState.value.isReceivedUnavailable)
    }

    /** A confirmed empty Received query remains distinguishable from an unavailable Sent query. */
    @Test fun tabsEmptyReceivedAndUnavailableSent() {
        fakeFirestoreAdapter.sentUnavailable = true
        val remoteOnly = DefaultCapsuleRepository(sessionTracker, fakeAuthAdapter, fakeFirestoreAdapter)
        val model = S03CapsuleListViewModel(sessionTracker, remoteOnly)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(model.uiState.value.isReceivedUnavailable)
        assertTrue(model.uiState.value.isSentUnavailable)
    }

    /** Exercises the interval before the session flow collector processes reauthentication. */
    @Test fun tabsImmediateRefreshClearsOldGeneration() {
        val row = CapsuleSummary("old", userA, userA, CapsuleLock(LockMode.TIME), SendStatus.SENT,
            availability = Availability.AVAILABLE)
        fakeFirestoreAdapter.sentList[userA] = listOf(row)
        fakeFirestoreAdapter.receivedList[userA] = listOf(row)
        val backend = withPendingSend()
        val model = S03CapsuleListViewModel(sessionTracker, backend,
            ForegroundRecoveryCoordinator(backend, sessionTracker, ioDispatcher = testDispatcher))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, model.uiState.value.unresolvedOperations.size)
        sessionTracker.startSession(userA)
        model.loadLists(explicitRefresh = true)
        val state = model.uiState.value
        assertEquals(sessionTracker.currentSession(), state.sessionSnapshot)
        assertTrue(state.sentList.isEmpty() && state.receivedList.isEmpty())
        assertTrue(state.unresolvedOperations.isEmpty())
        assertEquals(null, state.errorMessage)
    }

    /** Provide a pending Sent operation alongside the real production queries. */
    private fun withPendingSend(): CapsuleRepository = object : CapsuleRepository by repository {
        override suspend fun recoverPublications(targetSession: SessionSnapshot) =
            OperationResult.Success(listOf(RecoveredPublication("pending", PublicationPhase.FAILED, null, true)))
    }

    /** The Received empty label remains visible when the Sent tab has a pending operation. */
    @Test fun tabsPendingSendDoesNotHideReceivedEmptyView() {
        val activity = openScreen(withPendingSend())
        val label = activity.findViewById<TextView>(R.id.tvEmptyList)
        assertNotNull("Received empty state is attached", label)
        assertEquals(View.VISIBLE, label.visibility)
        assertEquals(activity.getString(R.string.s03_empty_received), label.text.toString())
    }

    /** Start the real activity and inject only its external data boundaries. */
    private fun openScreen(backend: CapsuleRepository): MainActivity {
        val app = RuntimeEnvironment.getApplication() as TimeCapsuleApplication
        val storage = object : StorageAdapter { override fun isAvailable() = false }
        val manager = AccountSessionManager(fakeAuthAdapter, fakeFirestoreAdapter, sessionTracker,
            storageDispatcher = testDispatcher)
        app.setContainerForTesting(AppDependencyContainer(sessionTracker, fakeAuthAdapter, fakeFirestoreAdapter,
            storage, DefaultLocationAdapter(app), retentionAdapter, backend, manager,
            recoveryCoordinator = ForegroundRecoveryCoordinator(backend, sessionTracker, ioDispatcher = testDispatcher)))
        val mainAct = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        settleScreen()
        return mainAct
    }

    /** Drain both the ViewModel dispatcher and Android fragment transactions. */
    private fun settleScreen() {
        repeat(4) {
            testDispatcher.scheduler.advanceUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun offlineReviewForTimeLocationAndBothModesPreservesLockModeAndTitle() = runBlocking {
        val sessionA = sessionTracker.currentSession()
        val now = Instant.now()

        val save1 = retentionAdapter.saveRetainedContent(
            targetSession = sessionA,
            capsuleId = "cap-time",
            verifiedUnlockedAt = now,
            text = "Time message",
            photoBytes = null,
            photoFormat = null,
            title = "Time Capsule Title",
            lockMode = LockMode.TIME,
            senderDisplayName = "Sender Jamie",
            createdAt = now.minusSeconds(1000)
        )
        assertTrue(save1 is OperationResult.Success)

        val save2 = retentionAdapter.saveRetainedContent(
            targetSession = sessionA,
            capsuleId = "cap-loc",
            verifiedUnlockedAt = now,
            text = "Location message",
            photoBytes = null,
            photoFormat = null,
            title = "Location Capsule Title",
            lockMode = LockMode.LOCATION,
            senderDisplayName = "Sender Alex",
            createdAt = now.minusSeconds(500)
        )
        assertTrue(save2 is OperationResult.Success)

        val save3 = retentionAdapter.saveRetainedContent(
            targetSession = sessionA,
            capsuleId = "cap-both",
            verifiedUnlockedAt = now,
            text = "Both message",
            photoBytes = null,
            photoFormat = null,
            title = "Both Capsule Title",
            lockMode = LockMode.TIME_AND_LOCATION,
            senderDisplayName = "Sender Sam",
            createdAt = now.minusSeconds(200)
        )
        assertTrue(save3 is OperationResult.Success)

        fakeFirestoreAdapter.isNetworkDown = true

        val listsRes = repository.lists(userA, sessionA)
        assertTrue(listsRes is OperationResult.Success)
        val snapshot = (listsRes as OperationResult.Success).data
        assertEquals(3, snapshot.received.size)

        val capTime = snapshot.received.first { it.capsuleId == "cap-time" }
        assertEquals("Time Capsule Title", capTime.title)
        assertEquals(LockMode.TIME, capTime.lock?.mode)
        assertEquals("Sender Jamie", capTime.senderDisplayName)

        val capLoc = snapshot.received.first { it.capsuleId == "cap-loc" }
        assertEquals("Location Capsule Title", capLoc.title)
        assertEquals(LockMode.LOCATION, capLoc.lock?.mode)
        assertEquals("Sender Alex", capLoc.senderDisplayName)

        val capBoth = snapshot.received.first { it.capsuleId == "cap-both" }
        assertEquals("Both Capsule Title", capBoth.title)
        assertEquals(LockMode.TIME_AND_LOCATION, capBoth.lock?.mode)
        assertEquals("Sender Sam", capBoth.senderDisplayName)
    }

    @Test
    fun validUnlockMarkerWithMissingPhotoPreservesUnlockedStateAndAllowsRetry() = runBlocking {
        val sessionA = sessionTracker.currentSession()
        val now = Instant.now()
        val validPng = imageFixture("png")

        val saveRes = retentionAdapter.saveRetainedContent(
            targetSession = sessionA,
            capsuleId = "cap-photo-missing",
            verifiedUnlockedAt = now,
            text = "Photo caption",
            photoBytes = validPng,
            photoFormat = OriginalPhotoFormat.PNG,
            title = "Photo Capsule",
            lockMode = LockMode.TIME
        )
        assertTrue(saveRes is OperationResult.Success)

        val accountHash = sha256(userA.toByteArray(Charsets.UTF_8))
        val photoFile = File(rootDir, "$accountHash/cap-photo-missing/content.photo")
        assertTrue(photoFile.exists())
        photoFile.delete()
        assertFalse(photoFile.exists())

        fakeFirestoreAdapter.isNetworkDown = true
        val summaryRes = repository.getSummary("cap-photo-missing", sessionA)
        assertTrue(summaryRes is OperationResult.Success)
        val summary = (summaryRes as OperationResult.Success).data
        assertEquals(Availability.AVAILABLE, summary.availability)
        assertEquals("Photo Capsule", summary.title)

        val contentRes = repository.readContent("cap-photo-missing", sessionA)
        assertTrue(contentRes is OperationResult.Unavailable)
        assertTrue((contentRes as OperationResult.Unavailable).message.contains("unavailable"))
    }

    @Test
    fun corruptedRetainedPhotoFailsIntegrityCheckAndPreventsRead() = runBlocking {
        val sessionA = sessionTracker.currentSession()
        val now = Instant.now()
        val validPng = imageFixture("png")

        val saveRes = retentionAdapter.saveRetainedContent(
            targetSession = sessionA,
            capsuleId = "cap-corrupt",
            verifiedUnlockedAt = now,
            text = "Caption",
            photoBytes = validPng,
            photoFormat = OriginalPhotoFormat.PNG
        )
        assertTrue(saveRes is OperationResult.Success)

        val accountHash = sha256(userA.toByteArray(Charsets.UTF_8))
        val photoFile = File(rootDir, "$accountHash/cap-corrupt/content.photo")
        assertTrue(photoFile.exists())
        photoFile.writeBytes(byteArrayOf(0, 1, 2, 3, 4, 5))

        val readRes = retentionAdapter.readRetainedContent(sessionA, "cap-corrupt")
        assertTrue(readRes is OperationResult.Unavailable)
    }

    @Test
    fun networkFailurePermitsOfflineFallbackWhereasAuthoritativeDenialBlocksFallback() = runBlocking {
        val sessionA = sessionTracker.currentSession()
        val now = Instant.now()

        val saveRes = retentionAdapter.saveRetainedContent(
            targetSession = sessionA,
            capsuleId = "cap-denied-test",
            verifiedUnlockedAt = now,
            text = "Retained text",
            photoBytes = null,
            photoFormat = null,
            title = "Retained Title"
        )
        assertTrue(saveRes is OperationResult.Success)

        fakeFirestoreAdapter.isNetworkDown = true
        val offlineSummary = repository.getSummary("cap-denied-test", sessionA)
        assertTrue(offlineSummary is OperationResult.Success)
        assertEquals("Retained Title", (offlineSummary as OperationResult.Success).data.title)

        fakeFirestoreAdapter.isNetworkDown = false
        fakeFirestoreAdapter.accessDeniedForSummary = true
        val deniedSummary = repository.getSummary("cap-denied-test", sessionA)
        assertTrue(deniedSummary is OperationResult.Denied)
    }

    @Test
    fun staleAndCrossAccountRetentionRequestsDenied() = runBlocking {
        val sessionA = sessionTracker.currentSession()
        val now = Instant.now()

        val saveRes = retentionAdapter.saveRetainedContent(
            targetSession = sessionA,
            capsuleId = "cap-private-a",
            verifiedUnlockedAt = now,
            text = "User A Private Message",
            photoBytes = null,
            photoFormat = null
        )
        assertTrue(saveRes is OperationResult.Success)

        sessionTracker.startSession(userB)
        val sessionB = sessionTracker.currentSession()

        val crossRead = retentionAdapter.readRetainedContent(sessionB, "cap-private-a")
        assertTrue(crossRead is OperationResult.Denied)

        val crossList = retentionAdapter.listVerifiedRecipientEntries(sessionB)
        assertTrue(crossList is OperationResult.Success)
        assertTrue((crossList as OperationResult.Success).data.isEmpty())
    }

    @Test
    fun multipleRetainedPhotoCapsulesEnumeratedWithoutLoadingPhotoBytes() = runBlocking {
        val sessionA = sessionTracker.currentSession()
        val now = Instant.now()
        val samplePhoto = imageFixture("png")

        for (i in 1..5) {
            val saveRes = retentionAdapter.saveRetainedContent(
                targetSession = sessionA,
                capsuleId = "cap-multi-$i",
                verifiedUnlockedAt = now.plusSeconds(i.toLong()),
                text = "Caption $i",
                photoBytes = samplePhoto,
                photoFormat = OriginalPhotoFormat.PNG,
                title = "Title $i"
            )
            assertTrue(saveRes is OperationResult.Success)
        }

        val summariesRes = retentionAdapter.listVerifiedRecipientEntries(sessionA)
        assertTrue(summariesRes is OperationResult.Success)
        val list = (summariesRes as OperationResult.Success).data
        assertEquals(5, list.size)

        for (summary in list) {
            assertTrue(summary.hasPhoto)
            assertTrue(summary.isContentAvailable)
            assertNotNull(summary.title)
        }
    }

    @Test
    fun timeLockVerificationReturnsLockedWaitingTime() = runBlocking {
        val now = Instant.now()
        fakeFirestoreAdapter.summaries["cap-time-locked"] = CapsuleSummary(
            "cap-time-locked", userB, userA, CapsuleLock(LockMode.TIME, now.plusSeconds(3600)),
            SendStatus.SENT, availability = Availability.LOCKED, title = "Time Locked"
        )
        fakeFirestoreAdapter.requirements["cap-time-locked"] = RecipientUnlockRequirements(
            "cap-time-locked", userA, unlocked = false, lockMode = LockMode.TIME, unlockAt = now.plusSeconds(3600)
        )
        fakeFirestoreAdapter.unlockVerificationResponse = OperationResult.Denied("Unlock authorization denied or unlock time not reached.")

        val fakeRepo = object : CapsuleRepository {
            override fun session() = sessionTracker.currentSession()
            override suspend fun getRecipient(uid: String) = OperationResult.Invalid("")
            override suspend fun publish(frozenOperation: CapsuleDraft) = OperationResult.Invalid("")
            override suspend fun reconcile(capsuleId: String) = OperationResult.Invalid("")
            override suspend fun lists(uid: String) = OperationResult.Invalid("")
            override suspend fun verifyUnlockOutcome(capsuleId: String, targetSession: SessionSnapshot) =
                repository.verifyUnlockOutcome(capsuleId, targetSession)
            override suspend fun getSummary(capsuleId: String, targetSession: SessionSnapshot): OperationResult<CapsuleSummary> {
                val sum = fakeFirestoreAdapter.summaries[capsuleId] ?: return OperationResult.Invalid("Not found")
                return OperationResult.Success(sum)
            }
            override suspend fun readContent(capsuleId: String) = readContent(capsuleId, sessionTracker.currentSession())
            override suspend fun readContent(capsuleId: String, targetSession: SessionSnapshot): OperationResult<ProtectedCapsuleContent> {
                val c = fakeFirestoreAdapter.protectedContent[capsuleId] ?: return OperationResult.Invalid("Not found")
                return OperationResult.Success(c)
            }
        }

        val fakeCoordinator = ForegroundRecoveryCoordinator(
            fakeRepo, sessionTracker, ioDispatcher = testDispatcher
        )
        val viewModel = S07CapsuleDetailViewModel(sessionTracker, fakeRepo, fakeCoordinator)
        viewModel.loadDetail("cap-time-locked")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isUnlocked)
        assertEquals(DetailStatus.LOCKED_WAITING_TIME, state.status)
        assertTrue(state.unlockReason?.contains("unlock time not reached", true) == true)

        // Verify subsequent successful verification works
        fakeFirestoreAdapter.unlockVerificationResponse = OperationResult.Success(
            UnlockVerificationOutcome(Availability.AVAILABLE, UnlockConfirmationKind.FIRST_UNLOCK_CONFIRMED)
        )
        fakeFirestoreAdapter.summaries["cap-time-locked"] = CapsuleSummary(
            "cap-time-locked", userB, userA, CapsuleLock(LockMode.TIME, now.plusSeconds(3600)),
            SendStatus.SENT, availability = Availability.AVAILABLE, title = "Time Unlocked"
        )
        fakeFirestoreAdapter.requirements["cap-time-locked"] = RecipientUnlockRequirements(
            "cap-time-locked", userA, unlocked = true, lockMode = LockMode.TIME, unlockAt = now.plusSeconds(3600), unlockedAt = now
        )
        fakeFirestoreAdapter.protectedContent["cap-time-locked"] = ProtectedCapsuleContent("cap-time-locked", "Unlocked Text", null)

        viewModel.loadDetail("cap-time-locked")
        testDispatcher.scheduler.advanceUntilIdle()

        val unlockedState = viewModel.uiState.value
        assertTrue(unlockedState.isUnlocked)
        assertEquals("Unlocked Text", unlockedState.content?.text)

        // Verify genuine non-participant denial remains ACCESS_DENIED
        fakeFirestoreAdapter.unlockVerificationResponse = OperationResult.Denied("Not participant")
        fakeFirestoreAdapter.summaries["cap-denied-user"] = CapsuleSummary(
            "cap-denied-user", userB, "other-user", CapsuleLock(LockMode.TIME), SendStatus.SENT, availability = Availability.LOCKED
        )
        fakeFirestoreAdapter.requirements["cap-denied-user"] = RecipientUnlockRequirements(
            "cap-denied-user", "other-user", unlocked = false, lockMode = LockMode.TIME
        )

        viewModel.loadDetail("cap-denied-user")
        testDispatcher.scheduler.advanceUntilIdle()

        val deniedState = viewModel.uiState.value
        assertEquals(DetailStatus.ACCESS_DENIED, deniedState.status)
    }

    @Test
    fun bothLockVerificationReturnsLockedWaitingBothAndRetryUnlocks() = runBlocking {
        val now = Instant.now()
        val locTarget = LocationTarget(latitude = 37.5665, longitude = 126.9780, radiusMeters = 150.0)
        fakeFirestoreAdapter.summaries["cap-both-locked"] = CapsuleSummary(
            "cap-both-locked", userB, userA, CapsuleLock(LockMode.TIME_AND_LOCATION, now.plusSeconds(3600), location = locTarget),
            SendStatus.SENT, availability = Availability.LOCKED, title = "Both Locked"
        )
        fakeFirestoreAdapter.requirements["cap-both-locked"] = RecipientUnlockRequirements(
            "cap-both-locked", userA, unlocked = false, lockMode = LockMode.TIME_AND_LOCATION, unlockAt = now.plusSeconds(3600), locationTarget = locTarget
        )
        fakeFirestoreAdapter.unlockVerificationResponse = OperationResult.Denied("Unlock authorization denied or unlock time not reached.")

        // A qualifying current fix lets the server actually decide the time condition.
        val location = object : com.example.timecapsule.data.adapter.LocationAdapter {
            override fun isFineLocationGranted() = true
            override fun isLocationAvailable() = true
            override suspend fun getCurrentLocation() = com.example.timecapsule.domain.model.LocationSampleResult(
                com.example.timecapsule.domain.model.LocationSampleStatus.SUCCESS,
                ForegroundLocationSample(locTarget.latitude, locTarget.longitude, 1000L, 5f))
        }
        val locationRepository = DefaultCapsuleRepository(sessionTracker, fakeAuthAdapter, fakeFirestoreAdapter,
            locationAdapter = location, clockNanosSupplier = { 1000L })
        val fakeRepo = object : CapsuleRepository by locationRepository {
            override suspend fun getSummary(capsuleId: String, targetSession: SessionSnapshot): OperationResult<CapsuleSummary> {
                val sum = fakeFirestoreAdapter.summaries[capsuleId] ?: return OperationResult.Invalid("Not found")
                return OperationResult.Success(sum)
            }
            override suspend fun readContent(capsuleId: String, targetSession: SessionSnapshot): OperationResult<ProtectedCapsuleContent> {
                val c = fakeFirestoreAdapter.protectedContent[capsuleId] ?: return OperationResult.Invalid("Not found")
                return OperationResult.Success(c)
            }
        }

        val fakeCoordinator = ForegroundRecoveryCoordinator(
            fakeRepo, sessionTracker, ioDispatcher = testDispatcher
        )
        val viewModel = S07CapsuleDetailViewModel(sessionTracker, fakeRepo, fakeCoordinator)
        viewModel.loadDetail("cap-both-locked")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isUnlocked)
        assertEquals(DetailStatus.LOCKED_WAITING_BOTH, state.status)

        // Perform Retry action when conditions are met
        fakeFirestoreAdapter.unlockVerificationResponse = OperationResult.Success(
            UnlockVerificationOutcome(Availability.AVAILABLE, UnlockConfirmationKind.FIRST_UNLOCK_CONFIRMED)
        )
        fakeFirestoreAdapter.requirements["cap-both-locked"] = RecipientUnlockRequirements(
            "cap-both-locked", userA, unlocked = true, lockMode = LockMode.TIME_AND_LOCATION, unlockAt = now, locationTarget = locTarget, unlockedAt = now
        )
        fakeFirestoreAdapter.protectedContent["cap-both-locked"] = ProtectedCapsuleContent("cap-both-locked", "Unlocked Both Text", null)

        viewModel.loadDetail("cap-both-locked")
        testDispatcher.scheduler.advanceUntilIdle()

        val unlockedState = viewModel.uiState.value
        assertTrue(unlockedState.isUnlocked)
        assertEquals("Unlocked Both Text", unlockedState.content?.text)
    }

    @Test
    fun locationLockConditionDisplaysCoordinatesAndRadius() = runBlocking {
        val now = Instant.now()
        val locTarget = LocationTarget(latitude = 37.5665, longitude = 126.9780, radiusMeters = 150.0)
        fakeFirestoreAdapter.summaries["detail-id"] = CapsuleSummary(
            "detail-id", userB, userA, CapsuleLock(LockMode.LOCATION, now.plusSeconds(3600), location = locTarget),
            SendStatus.SENT, availability = Availability.LOCKED, title = "Location Locked"
        )
        fakeFirestoreAdapter.requirements["detail-id"] = RecipientUnlockRequirements(
            "detail-id", userA, unlocked = false, lockMode = LockMode.LOCATION, locationTarget = locTarget
        )

        val activity = openDetail(repository)
        val lockText = activity.findViewById<TextView>(R.id.tvS07LockCondition)
        assertNotNull(lockText)
        assertTrue(lockText.text.contains("37.5665"))
        assertTrue(lockText.text.contains("126.9780"))
        assertTrue(lockText.text.contains("150 m radius"))
    }

    @Test
    fun offlineSelfSendAppearsInBothTabs() = runBlocking {
        val sessionA = sessionTracker.currentSession()
        val now = Instant.now()

        // 1. Save A->A self-send with authoritative UIDs
        val saveSelf = retentionAdapter.saveRetainedContent(
            targetSession = sessionA,
            capsuleId = "cap-self-a-a",
            verifiedUnlockedAt = now,
            text = "Self Send Note",
            photoBytes = null,
            photoFormat = null,
            title = "Self Send Title",
            lockMode = LockMode.TIME,
            senderUid = userA,
            recipientUid = userA,
            senderDisplayName = "Me",
            recipientDisplayName = "Me"
        )
        assertTrue(saveSelf is OperationResult.Success)

        // 2. Save B->A received capsule
        val saveReceived = retentionAdapter.saveRetainedContent(
            targetSession = sessionA,
            capsuleId = "cap-recv-b-a",
            verifiedUnlockedAt = now,
            text = "B Note",
            photoBytes = null,
            photoFormat = null,
            title = "B Title",
            lockMode = LockMode.TIME,
            senderUid = userB,
            recipientUid = userA,
            senderDisplayName = "User B",
            recipientDisplayName = "Me"
        )
        assertTrue(saveReceived is OperationResult.Success)

        // 3. Save legacy record with unknown sender (senderUid = null)
        val saveLegacy = retentionAdapter.saveRetainedContent(
            targetSession = sessionA,
            capsuleId = "cap-legacy-unknown",
            verifiedUnlockedAt = now,
            text = "Legacy Note",
            photoBytes = null,
            photoFormat = null,
            title = "Legacy Title",
            lockMode = LockMode.TIME,
            senderUid = null,
            recipientUid = userA
        )
        assertTrue(saveLegacy is OperationResult.Success)

        // Force network offline
        fakeFirestoreAdapter.isNetworkDown = true

        val listsRes = repository.lists(userA, sessionA)
        assertTrue(listsRes is OperationResult.Success)
        val snapshot = (listsRes as OperationResult.Success).data

        // A->A must appear in Received AND Sent tabs with same capsule ID
        val selfInReceived = snapshot.received.firstOrNull { it.capsuleId == "cap-self-a-a" }
        val selfInSent = snapshot.sent.firstOrNull { it.capsuleId == "cap-self-a-a" }
        assertNotNull(selfInReceived)
        assertNotNull(selfInSent)
        assertEquals("cap-self-a-a", selfInReceived?.capsuleId)
        assertEquals("cap-self-a-a", selfInSent?.capsuleId)

        // B->A must appear ONLY in Received
        val bInReceived = snapshot.received.firstOrNull { it.capsuleId == "cap-recv-b-a" }
        val bInSent = snapshot.sent.firstOrNull { it.capsuleId == "cap-recv-b-a" }
        assertNotNull(bInReceived)
        assertNull(bInSent)

        // Legacy record with unknown sender must NOT be inserted into Sent
        val legacyInSent = snapshot.sent.firstOrNull { it.capsuleId == "cap-legacy-unknown" }
        assertNull(legacyInSent)

        // Server-confirmed query results when online are not overwritten
        fakeFirestoreAdapter.isNetworkDown = false
        fakeFirestoreAdapter.sentList[userA] = listOf(
            CapsuleSummary("cap-online-sent", userA, userB, CapsuleLock(LockMode.TIME), SendStatus.SENT, title = "Online Sent")
        )
        fakeFirestoreAdapter.receivedList[userA] = listOf(
            CapsuleSummary("cap-online-recv", userB, userA, CapsuleLock(LockMode.TIME), SendStatus.SENT, title = "Online Recv")
        )

        val onlineRes = repository.lists(userA, sessionA)
        assertTrue(onlineRes is OperationResult.Success)
        val onlineSnapshot = (onlineRes as OperationResult.Success).data
        assertEquals(ListProvenance.SERVER_CONFIRMED, onlineSnapshot.sentProvenance)
        assertEquals(ListProvenance.SERVER_CONFIRMED, onlineSnapshot.receivedProvenance)
        assertEquals(1, onlineSnapshot.sent.size)
        assertEquals("cap-online-sent", onlineSnapshot.sent[0].capsuleId)
    }

    @Test
    fun successfulOnlineAssetRetryRestoresContentWithoutChangingUnlockedState() = runBlocking {
        val sessionA = sessionTracker.currentSession()
        val now = Instant.now().minusSeconds(500)

        val capSummary = CapsuleSummary(
            capsuleId = "cap-retry",
            senderUid = userB,
            recipientUid = userA,
            lock = CapsuleLock(LockMode.TIME),
            sendStatus = SendStatus.SENT,
            availability = Availability.AVAILABLE,
            title = "Retry Capsule",
            unlockedAt = now
        )
        fakeFirestoreAdapter.summaries["cap-retry"] = capSummary
        fakeFirestoreAdapter.requirements["cap-retry"] = RecipientUnlockRequirements(
            capsuleId = "cap-retry",
            recipientUid = userA,
            unlocked = true,
            lockMode = LockMode.TIME,
            unlockedAt = now
        )
        fakeFirestoreAdapter.protectedContent["cap-retry"] = ProtectedCapsuleContent(
            capsuleId = "cap-retry",
            text = "Restored message",
            photoObjectPath = null
        )

        val readRes = repository.readContent("cap-retry", sessionA)
        assertTrue(readRes is OperationResult.Success)
        val content = (readRes as OperationResult.Success).data
        assertEquals("Restored message", content.text)

        val retainedRes = retentionAdapter.readRetainedContent(sessionA, "cap-retry")
        assertTrue(retainedRes is OperationResult.Success)
        assertEquals(now, (retainedRes as OperationResult.Success).data.verifiedUnlockedAt)
    }

    @Test
    fun initialPhotoDownloadFailureAfterConfirmedUnlockThenOfflineProcessRecreation() = runBlocking {
        val session = sessionTracker.currentSession()
        val serverTime = Instant.parse("2026-10-15T08:00:00Z")

        val capSummary = CapsuleSummary(
            capsuleId = "cap-prod-fail",
            senderUid = userB,
            recipientUid = userA,
            lock = CapsuleLock(LockMode.TIME),
            sendStatus = SendStatus.SENT,
            availability = Availability.AVAILABLE,
            title = "Photo Production Fail",
            unlockedAt = serverTime,
            hasPhoto = true
        )
        fakeFirestoreAdapter.summaries["cap-prod-fail"] = capSummary
        fakeFirestoreAdapter.requirements["cap-prod-fail"] = RecipientUnlockRequirements(
            capsuleId = "cap-prod-fail",
            recipientUid = userA,
            unlocked = true,
            lockMode = LockMode.TIME,
            unlockedAt = serverTime
        )
        fakeFirestoreAdapter.protectedContent["cap-prod-fail"] = ProtectedCapsuleContent(
            capsuleId = "cap-prod-fail",
            text = "Text caption",
            photoObjectPath = "remote/photo.jpg",
            photoBytes = null // photo download fails!
        )

        // 1. Execute production repository readContent
        val initialRead = repository.readContent("cap-prod-fail", session)
        assertTrue(initialRead is OperationResult.Unavailable)

        // 2. Assert that the persisted marker timestamp equals the authoritative server timestamp
        val markerRes = retentionAdapter.listVerifiedRecipientEntries(session)
        assertTrue(markerRes is OperationResult.Success)
        val entries = (markerRes as OperationResult.Success).data
        assertEquals(1, entries.size)
        assertEquals(serverTime, entries[0].verifiedUnlockedAt)
        assertEquals("Photo Production Fail", entries[0].title)

        // 3. Recreate retention adapter & repository (simulating process recreation)
        val currentSnap = sessionTracker.currentSession()
        val newRetentionAdapter = DefaultLocalRetentionAdapter(
            root = rootDir,
            session = { sessionTracker.currentSession() }
        )
        val newRepository = DefaultCapsuleRepository(
            sessionTracker = sessionTracker,
            authAdapter = fakeAuthAdapter,
            firestoreAdapter = fakeFirestoreAdapter,
            retentionAdapter = newRetentionAdapter
        )

        // 4. Disconnect network
        fakeFirestoreAdapter.isNetworkDown = true

        // 5. Offline: unlocked entry is discoverable, but content is unavailable
        val offlineSummary = newRepository.getSummary("cap-prod-fail", currentSnap)
        assertTrue(offlineSummary is OperationResult.Success)
        assertEquals(Availability.AVAILABLE, (offlineSummary as OperationResult.Success).data.availability)

        val offlineContent = newRepository.readContent("cap-prod-fail", currentSnap)
        assertTrue(offlineContent is OperationResult.Unavailable)

        // 6. Reconnect network & fix photo download
        fakeFirestoreAdapter.isNetworkDown = false
        val validPng = imageFixture("png")
        fakeFirestoreAdapter.protectedContent["cap-prod-fail"] = ProtectedCapsuleContent(
            capsuleId = "cap-prod-fail",
            text = "Text caption",
            photoObjectPath = "remote/photo.jpg",
            photoBytes = validPng,
            photoFormat = OriginalPhotoFormat.PNG
        )

        // 7. Successful subsequent download
        val reconnectRead = newRepository.readContent("cap-prod-fail", currentSnap)
        assertTrue(reconnectRead is OperationResult.Success)
        val content = (reconnectRead as OperationResult.Success).data
        assertEquals("Text caption", content.text)
        assertNotNull(content.photoBytes)
    }

    @Test
    fun accountTransitionWhileViewModelsActiveClearsPreviousAccountStateImmediately() = runBlocking {
        val fakeRepo = object : CapsuleRepository {
            override fun session() = sessionTracker.currentSession()
            override suspend fun getRecipient(uid: String) = OperationResult.Invalid("")
            override suspend fun publish(frozenOperation: CapsuleDraft) = OperationResult.Invalid("")
            override suspend fun reconcile(capsuleId: String) = OperationResult.Invalid("")
            override suspend fun lists(uid: String) = lists(uid, sessionTracker.currentSession())
            override suspend fun lists(uid: String, targetSession: SessionSnapshot): OperationResult<CapsuleListsSnapshot> {
                val list = if (uid == userA) listOf(CapsuleSummary("cap-recv-a", userB, userA, CapsuleLock(LockMode.TIME), SendStatus.SENT, title = "Recv A", availability = Availability.AVAILABLE)) else emptyList()
                return OperationResult.Success(CapsuleListsSnapshot(sent = emptyList(), received = list))
            }
            override suspend fun getSummary(capsuleId: String) = getSummary(capsuleId, sessionTracker.currentSession())
            override suspend fun getSummary(capsuleId: String, targetSession: SessionSnapshot): OperationResult<CapsuleSummary> {
                if (targetSession.uid != userA) return OperationResult.Denied("Not user A")
                return OperationResult.Success(CapsuleSummary("cap-recv-a", userB, userA, CapsuleLock(LockMode.TIME), SendStatus.SENT, title = "Recv A", availability = Availability.AVAILABLE))
            }
            override suspend fun readContent(capsuleId: String) = readContent(capsuleId, sessionTracker.currentSession())
            override suspend fun readContent(capsuleId: String, targetSession: SessionSnapshot): OperationResult<ProtectedCapsuleContent> {
                if (targetSession.uid != userA) return OperationResult.Denied("Not user A")
                return OperationResult.Success(ProtectedCapsuleContent("cap-recv-a", "User A Secret", null))
            }
        }

        val listViewModel = S03CapsuleListViewModel(sessionTracker, fakeRepo)
        val detailViewModel = S07CapsuleDetailViewModel(sessionTracker, fakeRepo)

        listViewModel.loadLists()
        detailViewModel.loadDetail("cap-recv-a")

        testDispatcher.scheduler.advanceUntilIdle()

        // Assert User A's data was loaded
        assertEquals(1, listViewModel.uiState.value.receivedList.size)
        assertEquals("User A Secret", detailViewModel.uiState.value.content?.text)

        // Transition to User B
        sessionTracker.startSession(userB)

        testDispatcher.scheduler.advanceUntilIdle()

        val listState = listViewModel.uiState.value
        val detailState = detailViewModel.uiState.value

        assertEquals(userB, listState.sessionSnapshot?.uid)
        assertEquals(userB, detailState.sessionSnapshot?.uid)
        assertTrue(listState.receivedList.isEmpty())
        assertTrue(detailState.content == null)
    }

    @Test
    fun interruptedRetainedContentReplacementPreservesUnlockEvidenceWithoutAuthorizingIncompleteBytes() = runBlocking {
        val sessionA = sessionTracker.currentSession()
        val now = Instant.now().minusSeconds(1000)
        val initialPng = imageFixture("png")

        // 1. Save a valid complete text-and-photo capsule using real DefaultLocalRetentionAdapter
        val initialSave = retentionAdapter.saveRetainedContent(
            targetSession = sessionA,
            capsuleId = "cap-interrupt",
            verifiedUnlockedAt = now,
            text = "Initial Text",
            photoBytes = initialPng,
            photoFormat = OriginalPhotoFormat.PNG,
            title = "Initial Title",
            lockMode = LockMode.TIME,
            senderDisplayName = "User B",
            recipientDisplayName = "User A"
        )
        assertTrue(initialSave is OperationResult.Success)

        val initialRead = retentionAdapter.readRetainedContent(sessionA, "cap-interrupt")
        assertTrue(initialRead is OperationResult.Success)

        // 2 & 3. Inject I/O fault into production DefaultLocalRetentionAdapter seam AFTER commitment invalidation
        retentionAdapter.faultInjector = {
            throw IOException("Forced I/O fault mid-replacement after commitment invalidation!")
        }

        val updatedPng = imageFixture("png")
        val interruptedSave = retentionAdapter.saveRetainedContent(
            targetSession = sessionA,
            capsuleId = "cap-interrupt",
            verifiedUnlockedAt = now,
            text = "New Replacement Text",
            photoBytes = updatedPng,
            photoFormat = OriginalPhotoFormat.PNG,
            title = "Replacement Title",
            lockMode = LockMode.TIME
        )
        assertTrue(interruptedSave is OperationResult.Unavailable)

        // 4. Recreate retention adapter (simulating process restart)
        val newRetentionAdapter = DefaultLocalRetentionAdapter(
            root = rootDir,
            session = { sessionTracker.currentSession() }
        )

        // 5. Confirm unlock evidence survives, but reading incomplete content fails closed!
        val summaryRes = newRetentionAdapter.listVerifiedRecipientEntries(sessionA)
        assertTrue(summaryRes is OperationResult.Success)
        val summaries = (summaryRes as OperationResult.Success).data
        assertEquals(1, summaries.size)
        assertEquals(now, summaries[0].verifiedUnlockedAt)
        assertFalse(summaries[0].isContentAvailable) // Content NOT available due to invalidation!

        val contentRead = newRetentionAdapter.readRetainedContent(sessionA, "cap-interrupt")
        assertTrue(contentRead is OperationResult.Denied || contentRead is OperationResult.Unavailable)

        // 6 & 7. Refresh verified unlock evidence alone without content files
        val refreshMarker = newRetentionAdapter.saveVerifiedUnlockMarker(
            targetSession = sessionA,
            capsuleId = "cap-interrupt",
            verifiedUnlockedAt = now,
            title = "Replacement Title",
            lockMode = LockMode.TIME,
            hasPhoto = true
        )
        assertTrue(refreshMarker is OperationResult.Success)

        // Confirm refresh does NOT reactivate incomplete bytes!
        val refreshSummaries = (newRetentionAdapter.listVerifiedRecipientEntries(sessionA) as OperationResult.Success).data
        assertFalse(refreshSummaries[0].isContentAvailable)
        val refreshedRead = newRetentionAdapter.readRetainedContent(sessionA, "cap-interrupt")
        assertTrue(refreshedRead is OperationResult.Denied || refreshedRead is OperationResult.Unavailable)

        // 8. Clear fault injector, perform a successful authorized download and confirm offline re-view works
        retentionAdapter.faultInjector = null
        val cleanSave = retentionAdapter.saveRetainedContent(
            targetSession = sessionA,
            capsuleId = "cap-interrupt",
            verifiedUnlockedAt = now,
            text = "Final Replacement Text",
            photoBytes = updatedPng,
            photoFormat = OriginalPhotoFormat.PNG,
            title = "Replacement Title",
            lockMode = LockMode.TIME
        )
        assertTrue(cleanSave is OperationResult.Success)

        val finalRead = newRetentionAdapter.readRetainedContent(sessionA, "cap-interrupt")
        assertTrue(finalRead is OperationResult.Success)
        assertEquals("Final Replacement Text", (finalRead as OperationResult.Success).data.text)
    }

    @Test
    fun refreshingUnlockEvidenceForCommittedCapsulePreservesBothAssets() = runBlocking {
        val sessionA = sessionTracker.currentSession()
        val now = Instant.now().minusSeconds(500)
        val validPng = imageFixture("png")

        // 1. Save valid text and photo capsule
        val saveRes = retentionAdapter.saveRetainedContent(
            targetSession = sessionA,
            capsuleId = "cap-committed-refresh",
            verifiedUnlockedAt = now,
            text = "Committed Text",
            photoBytes = validPng,
            photoFormat = OriginalPhotoFormat.PNG,
            title = "Committed Title",
            lockMode = LockMode.TIME
        )
        assertTrue(saveRes is OperationResult.Success)

        // 2. Refresh unlock evidence with default hasText = false, hasPhoto = false
        val refreshRes = retentionAdapter.saveVerifiedUnlockMarker(
            targetSession = sessionA,
            capsuleId = "cap-committed-refresh",
            verifiedUnlockedAt = now,
            title = "Refreshed Title",
            lockMode = LockMode.TIME
        )
        assertTrue(refreshRes is OperationResult.Success)

        // 3. Disconnect network and recreate repository & retention adapter
        val newRetentionAdapter = DefaultLocalRetentionAdapter(
            root = rootDir,
            session = { sessionTracker.currentSession() }
        )
        val newRepository = DefaultCapsuleRepository(
            sessionTracker = sessionTracker,
            authAdapter = fakeAuthAdapter,
            firestoreAdapter = fakeFirestoreAdapter,
            retentionAdapter = newRetentionAdapter
        )
        fakeFirestoreAdapter.isNetworkDown = true

        // 4. Verify BOTH assets (text and photo) remain readable offline
        val offlineRead = newRepository.readContent("cap-committed-refresh", sessionA)
        assertTrue(offlineRead is OperationResult.Success)
        val content = (offlineRead as OperationResult.Success).data
        assertEquals("Committed Text", content.text)
        assertNotNull(content.photoBytes)
        assertEquals(OriginalPhotoFormat.PNG, content.photoFormat)
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    // --- Fakes ---

    private class FakeAuthAdapter(private var activeUid: String?) : AuthAdapter {
        override fun activeUid(): String? = activeUid
        override fun activeEmail(): String = "test@example.com"
        override fun activeDisplayName(): String = "Test User"
        var signInGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        var signUpCalls = 0
        var signInCalls = 0
        override suspend fun signIn(email: String, password: String): OperationResult<String> {
            signInCalls++
            signInGate?.await()
            activeUid = "reader"
            return OperationResult.Success("reader")
        }
        override suspend fun signUp(email: String, password: String, displayName: String): OperationResult<String> {
            signUpCalls++
            activeUid = "new-reader"
            return OperationResult.Success("new-reader")
        }
        var afterNameUpdate: (() -> Unit)? = null
        override suspend fun updateDisplayName(displayName: String): OperationResult<Unit> {
            afterNameUpdate?.invoke()
            return OperationResult.Success(Unit)
        }
        override fun signOut() { activeUid = null }
    }

    private class FakeFirestoreAdapter : FirestoreAdapter {
        var isNetworkDown = false
        var sentUnavailable = false
        var receivedUnavailable = false
        var accessDeniedForSummary = false
        var contentFailure: OperationResult<Nothing>? = null
        var unlockVerificationResponse: OperationResult<UnlockVerificationOutcome>? = null
        val sentList = mutableMapOf<String, List<CapsuleSummary>>()
        val receivedList = mutableMapOf<String, List<CapsuleSummary>>()
        val summaries = mutableMapOf<String, CapsuleSummary>()
        val requirements = mutableMapOf<String, RecipientUnlockRequirements>()
        val protectedContent = mutableMapOf<String, ProtectedCapsuleContent>()

        override fun isAvailable(): Boolean = !isNetworkDown
        override fun firestoreInstance(): FirebaseFirestore? = null
        var profileWrites = 0
        var failProfileWrite = false
        var profileResult: OperationResult<RecipientProfile>? = null
        override suspend fun getRecipientProfile(uid: String) = profileResult ?: OperationResult.Success(RecipientProfile(uid, "Test User"))
        override suspend fun saveRecipientProfile(profile: RecipientProfile): OperationResult<Unit> {
            profileWrites++
            if (failProfileWrite) return OperationResult.Unavailable("Directory unavailable")
            profileResult = OperationResult.Success(profile)
            return OperationResult.Success(Unit)
        }

        override suspend fun verifyRecipientUnlockDetailed(
            capsuleId: String,
            recipientUid: String,
            locationSample: ForegroundLocationSample?,
            clockNanosSupplier: () -> Long
        ): OperationResult<UnlockVerificationOutcome> {
            if (isNetworkDown) return OperationResult.Unavailable("Network down")
            unlockVerificationResponse?.let { return it }
            return OperationResult.Success(UnlockVerificationOutcome(Availability.AVAILABLE, UnlockConfirmationKind.ALREADY_UNLOCKED))
        }

        override suspend fun getSentCapsuleSummaries(senderUid: String): OperationResult<List<CapsuleSummary>> {
            if (isNetworkDown || sentUnavailable) return OperationResult.Unavailable("Network down")
            return OperationResult.Success(sentList[senderUid] ?: emptyList())
        }

        override suspend fun getReceivedCapsuleSummaries(recipientUid: String): OperationResult<List<CapsuleSummary>> {
            if (isNetworkDown || receivedUnavailable) return OperationResult.Unavailable("Network down")
            return OperationResult.Success(receivedList[recipientUid] ?: emptyList())
        }

        override suspend fun getCapsuleSummary(capsuleId: String, uid: String): OperationResult<CapsuleSummary> {
            if (accessDeniedForSummary) return OperationResult.Denied("Authoritative access denied for capsule summary.")
            if (isNetworkDown) return OperationResult.Unavailable("Network down")
            val sum = summaries[capsuleId] ?: return OperationResult.Unavailable("Summary not implemented in fake")
            if (sum.senderUid != uid && sum.recipientUid != uid) return OperationResult.Denied("Not participant")
            return OperationResult.Success(sum)
        }

        override suspend fun readRecipientUnlockRequirements(
            capsuleId: String,
            recipientUid: String
        ): OperationResult<RecipientUnlockRequirements> {
            if (accessDeniedForSummary) return OperationResult.Denied("Authoritative access denied for unlock requirements.")
            if (isNetworkDown) return OperationResult.Unavailable("Network down")
            val req = requirements[capsuleId] ?: return OperationResult.Invalid("Not found")
            if (req.recipientUid != recipientUid) return OperationResult.Denied("Not recipient")
            return OperationResult.Success(req)
        }

        override suspend fun readProtectedContent(
            capsuleId: String,
            recipientUid: String
        ): OperationResult<ProtectedCapsuleContent> {
            if (isNetworkDown) return OperationResult.Unavailable("Network down")
            contentFailure?.let { return it }
            val content = protectedContent[capsuleId] ?: return OperationResult.Invalid("Not found")
            return OperationResult.Success(content)
        }
    }
}
