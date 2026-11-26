package com.example.timecapsule.data.publication

import com.example.timecapsule.data.session.fixtureSession
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.timecapsule.MainActivity
import com.example.timecapsule.R
import com.example.timecapsule.config.FirebaseConfig
import com.example.timecapsule.data.adapter.DefaultStorageAdapter
import com.example.timecapsule.data.adapter.FirestoreCapsulePublication
import com.example.timecapsule.data.media.AndroidOriginalImageProbe
import com.example.timecapsule.data.media.OriginalPhotoStore
import com.example.timecapsule.data.media.OriginalPhotoUploads
import com.example.timecapsule.data.media.PhotoDraftJournal
import com.example.timecapsule.data.media.PhotoDraftRecord
import com.example.timecapsule.domain.model.CapsuleDraft
import com.example.timecapsule.domain.model.CapsuleLock
import com.example.timecapsule.domain.model.LocationTarget
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.domain.model.PublicationPhase
import com.example.timecapsule.domain.model.SendStatus
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot
import com.example.timecapsule.ui.draft.CapsuleDraftViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android SDK publication against the credential-free local demo backend; never skips offline. */
@RunWith(AndroidJUnit4::class)
class CapsulePublicationDeviceTest {
    private lateinit var context: Context
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var backend: FirestoreCapsulePublication
    private lateinit var root: File
    private lateinit var uid: String
    private lateinit var recipientUid: String

    /** Fails explicitly unless local services and a fresh authenticated demo account are available. */
    @Before fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        assertTrue(FirebaseConfig.setupDebugEmulatorConnection(context, true))
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseConfig.getMemoryOnlyFirestore(context)!!
        storage = FirebaseStorage.getInstance()
        withTimeout(30_000) {
            recipientUid = auth.createUserWithEmailAndPassword(
                "recipient-${UUID.randomUUID()}@example.test", "DeviceTest123!").await().user!!.uid
            firestore.collection("recipientDirectory").document(recipientUid)
                .set(mapOf("uid" to recipientUid, "displayName" to "Device recipient")).await()
            auth.signOut()
            uid = auth.createUserWithEmailAndPassword("device-${UUID.randomUUID()}@example.test",
                "DeviceTest123!").await().user!!.uid
            firestore.collection("recipientDirectory").document(uid)
                .set(mapOf("uid" to uid, "displayName" to "Device sender")).await()
        }
        backend = FirestoreCapsulePublication(firestore, auth)
        root = File(context.noBackupFilesDir, "publication-device-${UUID.randomUUID()}")
        assertTrue(root.mkdirs())
    }

    /** Deletes only this fixture's local directory; emulator records remain isolated demo test data. */
    @After fun cleanup() {
        if (::auth.isInitialized) auth.signOut()
        if (::root.isInitialized) root.deleteRecursively()
    }

    /** Gives every scenario a fresh identity and a time-independent location lock. */
    private fun draft(recipient: String = recipientUid) = CapsuleDraft(
        UUID.randomUUID().toString(), recipient, "Device fixture message", null,
        CapsuleLock(LockMode.LOCATION, location = LocationTarget(37.0, 127.0)),
        isRecipientConfirmed = true)

    /** Wires the same real SDK adapter, private store and coordinator used by the app. */
    private fun coordinator(custom: CapsulePublicationBackend = backend): CapsulePublicationCoordinator {
        val originals = OriginalPhotoStore(File(root, "photos"), AndroidOriginalImageProbe())
        val session = { SessionSnapshot(auth.currentUser?.uid, 1L) }
        return CapsulePublicationCoordinator(FrozenOperationJournal(File(root, "operations")), custom,
            OriginalPublicationMedia(OriginalPhotoUploads(originals, File(root, "uploads"),
                DefaultStorageAdapter(storage, firestore, auth), session)), session,
            responseTimeoutMillis = 20_000,
            drafts = PrivatePublicationDrafts(originals, PhotoDraftJournal(File(root, "drafts"))))
    }

    /** Confirms both paired documents and unchanged retry/reconciliation after journal recreation. */
    @Test fun textPublicationAndRestartReconciliation() = runBlocking {
        withTimeout(60_000) {
            val draft = draft()
            assertTrue(coordinator().publish(draft) is OperationResult.Success)
            val metadata = firestore.collection("capsules").document(draft.capsuleId)
                .get(Source.SERVER).await()
            val content = firestore.collection("protectedContent").document(draft.capsuleId)
                .get(Source.SERVER).await()
            assertTrue(metadata.exists())
            assertEquals(draft.text, content.getString("text"))
            assertEquals(metadata.getString("snapshotDigest"), content.getString("snapshotDigest"))
            assertEquals(OperationResult.Success(SendStatus.SENT), coordinator().reconcile(draft.capsuleId))
            assertTrue(coordinator().retry(draft.capsuleId) is OperationResult.Success)
        }
    }

    /** Self-send ACK can be reconciled from metadata while backend rules still deny protected reads. */
    @Test fun selfSendStaysProtectedAfterSuccess() = runBlocking {
        withTimeout(60_000) {
            val draft = draft(uid)
            assertTrue(coordinator().publish(draft) is OperationResult.Success)
            assertEquals(OperationResult.Success(SendStatus.SENT), coordinator().reconcile(draft.capsuleId))
            try {
                firestore.collection("protectedContent").document(draft.capsuleId).get(Source.SERVER).await()
                fail("Locked self-send content must remain protected.")
            } catch (expected: FirebaseFirestoreException) {
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, expected.code)
            }
        }
    }

    /** Real Android encoding, original retention, Storage upload ACK and authorized download preserve bytes. */
    @Test fun photoPublicationAndAuthenticatedDownload() = runBlocking {
        withTimeout(60_000) {
            val image = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            val bytes = try {
                ByteArrayOutputStream().use {
                    assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it))
                    it.toByteArray()
                }
            } finally { image.recycle() }
            val draft = draft()
            val photo = OriginalPhotoStore(File(root, "photos"), AndroidOriginalImageProbe())
                .retain(fixtureSession(uid), uid, draft.capsuleId, "image/png", bytes.inputStream())
            assertTrue(coordinator().publish(draft.copy(originalPhoto = photo)) is OperationResult.Success)
            val metadata = firestore.collection("capsules").document(draft.capsuleId)
                .get(Source.SERVER).await()
            val path = metadata.getString("photoPath")!!
            assertArrayEquals(bytes, storage.reference.child(path).getBytes(5_242_880).await())
            val record = FrozenOperationJournal(File(root, "operations")).load(uid, draft.capsuleId)!!
            assertTrue(record.photoAcknowledged)
            assertEquals(PublicationPhase.SENT, record.phase)
        }
    }

    /** A deliberately lost client response after a real batch commit resolves without duplicate delivery. */
    @Test fun lostResponseReconcilesRealCommittedBatch() = runBlocking {
        withTimeout(60_000) {
            var writes = 0
            val wrapped = object : CapsulePublicationBackend {
                override suspend fun publish(operation: com.example.timecapsule.domain.model.FrozenCapsuleOperation) {
                    writes++
                    backend.publish(operation)
                    throw IOException("Simulated lost response after actual server commit")
                }
                override suspend fun inspect(operation: com.example.timecapsule.domain.model.FrozenCapsuleOperation) =
                    backend.inspect(operation)
            }
            val draft = draft()
            assertTrue(coordinator(wrapped).publish(draft) is OperationResult.Success)
            assertTrue(coordinator(wrapped).retry(draft.capsuleId) is OperationResult.Success)
            assertEquals(1, writes)
        }
    }

    /** Actual rules reject an expired time lock; only then can unchanged content receive a new draft ID. */
    @Test fun rejectedBatchCreatesSafeEditableReplacement() = runBlocking {
        withTimeout(60_000) {
            val draft = draft().copy(lock = CapsuleLock(LockMode.TIME, Instant.now().plusSeconds(2)))
            PhotoDraftJournal(File(root, "drafts")).save(fixtureSession(uid), uid,
                PhotoDraftRecord(draft.capsuleId, "", draft.text, null))
            val delayed = object : CapsulePublicationBackend {
                override suspend fun publish(operation: com.example.timecapsule.domain.model.FrozenCapsuleOperation) {
                    delay(3_000)
                    backend.publish(operation)
                }
                override suspend fun inspect(operation: com.example.timecapsule.domain.model.FrozenCapsuleOperation) =
                    backend.inspect(operation)
            }
            coordinator(delayed).publish(draft)
            val journal = FrozenOperationJournal(File(root, "operations"))
            assertEquals(PublicationPhase.FAILED, journal.load(uid, draft.capsuleId)!!.phase)
            assertFalse(firestore.collection("capsules").document(draft.capsuleId)
                .get(Source.SERVER).await().exists())
            val replacement = coordinator().abandonForNewDraft(draft.capsuleId) as OperationResult.Success
            assertTrue(replacement.data.capsuleId != draft.capsuleId)
            assertEquals(draft.text, replacement.data.text)
            assertFalse(replacement.data.isRecipientConfirmed)
        }
    }

    /** Polls only app-owned state with a bounded wait; no network or UI test is silently skipped. */
    private fun awaitState(scenario: ActivityScenario<MainActivity>,
        predicate: (com.example.timecapsule.ui.draft.CapsuleDraftUiState) -> Boolean) {
        val until = android.os.SystemClock.elapsedRealtime() + 30_000
        while (android.os.SystemClock.elapsedRealtime() < until) {
            var ready = false
            scenario.onActivity {
                ready = predicate(ViewModelProvider(it)[CapsuleDraftViewModel::class.java].uiState.value)
            }
            if (ready) return
            Thread.sleep(100)
        }
        fail("Expected app-owned publication state was not reached before timeout.")
    }

    /** Taps the real S-06 send control and verifies completed protected content stays hidden after recreation. */
    @Test fun sendButtonAndActivityRecreationRecoverCompletedSend() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitState(scenario) { it.editable && !it.photoBusy }
            scenario.onActivity {
                val model = ViewModelProvider(it)[CapsuleDraftViewModel::class.java]
                model.updateText("UI device fixture")
                model.updateLockMode(LockMode.LOCATION)
                model.updateLocation(LocationTarget(37.0, 127.0), "Fixture place")
                it.navigateToConfirmSend()
            }
            onView(withId(R.id.btnSendCapsule)).perform(scrollTo(), click())
            awaitState(scenario) { it.publicationPhase == PublicationPhase.SENT && !it.publicationBusy }
            scenario.recreate()
            awaitState(scenario) {
                it.publicationPhase == PublicationPhase.SENT && !it.recoveryBusy && it.text.isEmpty()
            }
        }
    }
}
