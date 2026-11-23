package com.example.timecapsule.data.adapter

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.example.timecapsule.config.FirebaseConfig
import com.example.timecapsule.domain.model.Availability
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.result.OperationResult
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Android Instrumentation Integration tests executing against live Firebase Auth & Firestore Emulators.
 * Verifies Issue #15 server-authorized TIME unlock transactions, early unlock denial,
 * concurrent unlock idempotency, non-recipient authorization denial, protected content access rules,
 * and metadata immutability.
 */
@RunWith(AndroidJUnit4::class)
class FirebaseEmulatorUnlockIntegrationTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setupEmulatorConnection() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            if (Build.VERSION.SDK_INT >= 37) {
                InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                    context.packageName, "android.permission.ACCESS_LOCAL_NETWORK")
            }
            val initialized = FirebaseConfig.setupDebugEmulatorConnection(context, true)
            assertTrue("Firebase debug emulator connection must succeed for tests", initialized)
        }
    }

    @get:Rule
    val grantLocalNetworkPermissionRule: TestRule =
        if (Build.VERSION.SDK_INT >= 37) {
            GrantPermissionRule.grant("android.permission.ACCESS_LOCAL_NETWORK")
        } else TestRule { statement, _ -> statement }

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var authAdapter: DefaultAuthAdapter
    private lateinit var firestoreAdapter: DefaultFirestoreAdapter

    @Before
    fun setUp() {
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        authAdapter = DefaultAuthAdapter { auth }
        firestoreAdapter = DefaultFirestoreAdapter { InstrumentationRegistry.getInstrumentation().targetContext }
    }

    private fun createAndSignInUser(email: String, name: String): String = runBlocking {
        val result = authAdapter.signUp(email, "password123", name)
        if (result !is OperationResult.Success) {
            val signInResult = authAdapter.signIn(email, "password123")
            if (signInResult is OperationResult.Success) {
                return@runBlocking signInResult.data
            }
            fail("Failed to setup test user ($email): $result")
            error("Unreachable")
        } else {
            return@runBlocking result.data
        }
    }

    private suspend fun seedTestCapsule(
        capsuleId: String,
        senderUid: String,
        senderEmail: String,
        senderName: String,
        recipientUid: String,
        recipientEmail: String,
        recipientName: String,
        futureUnlockSeconds: Long = 3L,
        lockMode: String = "TIME"
    ) {
        // 1. Save recipient directory profile while authenticated as sender owner
        createAndSignInUser(senderEmail, senderName)
        val saveSenderRes = firestoreAdapter.saveRecipientProfile(RecipientProfile(senderUid, senderName))
        assertTrue("Sender profile save must succeed", saveSenderRes is OperationResult.Success)

        // 2. Save recipient directory profile while authenticated as recipient owner
        createAndSignInUser(recipientEmail, recipientName)
        val saveRecipientRes = firestoreAdapter.saveRecipientProfile(RecipientProfile(recipientUid, recipientName))
        assertTrue("Recipient profile save must succeed", saveRecipientRes is OperationResult.Success)

        // 3. Re-authenticate as sender to perform capsule metadata and content creation
        createAndSignInUser(senderEmail, senderName)

        // Set unlockAt in near future (e.g. 3s) so firestore.rules creation constraint (unlockAt > request.time) passes
        val unlockAtTimestamp = Timestamp(System.currentTimeMillis() / 1000 + futureUnlockSeconds, 0)

        val metadata = mapOf(
            "capsuleId" to capsuleId,
            "senderId" to senderUid,
            "recipientId" to recipientUid,
            "title" to "Test Capsule Title",
            "lockMode" to lockMode,
            "unlockAt" to unlockAtTimestamp,
            "location" to null,
            "hasPhoto" to false,
            "photoPath" to null,
            "createdAt" to FieldValue.serverTimestamp(),
            "creationZone" to "Asia/Seoul",
            "snapshotDigest" to "a".repeat(64),
            "unlocked" to false
        )

        val content = mapOf(
            "capsuleId" to capsuleId,
            "text" to "Protected message text",
            "photoObjectPath" to null,
            "snapshotDigest" to "a".repeat(64)
        )

        val batch = firestore.batch()
        batch.set(firestore.collection("capsules").document(capsuleId), metadata)
        batch.set(firestore.collection("protectedContent").document(capsuleId), content)
        batch.commit().await()
    }

    @Test
    fun test1_serverAuthorizedTimeUnlockSucceedsAtOrAfterDeadline() = runBlocking {
        val testId = UUID.randomUUID().toString().take(8)
        val senderEmail = "sender_t1_$testId@example.com"
        val recipientEmail = "recipient_t1_$testId@example.com"
        val capsuleId = "capsule-t1-$testId"

        val senderUid = createAndSignInUser(senderEmail, "Sender 1")
        val recipientUid = createAndSignInUser(recipientEmail, "Recipient 1")

        seedTestCapsule(capsuleId, senderUid, senderEmail, "Sender 1", recipientUid, recipientEmail, "Recipient 1", futureUnlockSeconds = 3L)

        // Wait for unlock deadline to elapse on server
        delay(3500L)

        createAndSignInUser(recipientEmail, "Recipient 1")
        val result = firestoreAdapter.verifyRecipientUnlock(capsuleId, recipientUid)

        assertTrue("Recipient unlock must succeed after deadline", result is OperationResult.Success)
        assertEquals(Availability.AVAILABLE, (result as OperationResult.Success).data)

        val doc = firestore.collection("capsules").document(capsuleId).get().await()
        assertEquals(true, doc.getBoolean("unlocked"))
        assertNotNull(doc.getTimestamp("unlockedAt"))
    }

    @Test
    fun test2_earlyUnlockDeniedBeforeDeadline() = runBlocking {
        val testId = UUID.randomUUID().toString().take(8)
        val senderEmail = "sender_t2_$testId@example.com"
        val recipientEmail = "recipient_t2_$testId@example.com"
        val capsuleId = "capsule-t2-$testId"

        val senderUid = createAndSignInUser(senderEmail, "Sender 2")
        val recipientUid = createAndSignInUser(recipientEmail, "Recipient 2")

        seedTestCapsule(capsuleId, senderUid, senderEmail, "Sender 2", recipientUid, recipientEmail, "Recipient 2", futureUnlockSeconds = 3600L)

        createAndSignInUser(recipientEmail, "Recipient 2")
        val result = firestoreAdapter.verifyRecipientUnlock(capsuleId, recipientUid)

        assertTrue("Early unlock before deadline must be denied", result is OperationResult.Denied)
    }

    @Test
    fun test3_repeatedAndConcurrentUnlockIsIdempotent() = runBlocking {
        val testId = UUID.randomUUID().toString().take(8)
        val senderEmail = "sender_t3_$testId@example.com"
        val recipientEmail = "recipient_t3_$testId@example.com"
        val capsuleId = "capsule-t3-$testId"

        val senderUid = createAndSignInUser(senderEmail, "Sender 3")
        val recipientUid = createAndSignInUser(recipientEmail, "Recipient 3")

        seedTestCapsule(capsuleId, senderUid, senderEmail, "Sender 3", recipientUid, recipientEmail, "Recipient 3", futureUnlockSeconds = 3L)

        delay(3500L)

        createAndSignInUser(recipientEmail, "Recipient 3")

        val job1 = async { firestoreAdapter.verifyRecipientUnlock(capsuleId, recipientUid) }
        val job2 = async { firestoreAdapter.verifyRecipientUnlock(capsuleId, recipientUid) }
        val results = awaitAll(job1, job2)

        assertTrue("First concurrent unlock must succeed", results[0] is OperationResult.Success)
        assertTrue("Second concurrent unlock must succeed", results[1] is OperationResult.Success)
        assertEquals(Availability.AVAILABLE, (results[0] as OperationResult.Success).data)
        assertEquals(Availability.AVAILABLE, (results[1] as OperationResult.Success).data)

        val doc = firestore.collection("capsules").document(capsuleId).get().await()
        assertEquals(true, doc.getBoolean("unlocked"))
        assertNotNull(doc.getTimestamp("unlockedAt"))
    }

    @Test
    fun test4_unauthorizedCallerDenied() = runBlocking {
        val testId = UUID.randomUUID().toString().take(8)
        val senderEmail = "sender_t4_$testId@example.com"
        val recipientEmail = "recipient_t4_$testId@example.com"
        val outsiderEmail = "outsider_t4_$testId@example.com"
        val capsuleId = "capsule-t4-$testId"

        val senderUid = createAndSignInUser(senderEmail, "Sender 4")
        val recipientUid = createAndSignInUser(recipientEmail, "Recipient 4")

        seedTestCapsule(capsuleId, senderUid, senderEmail, "Sender 4", recipientUid, recipientEmail, "Recipient 4", futureUnlockSeconds = 3L)

        delay(3500L)

        // Sender attempting recipient unlock -> denied
        createAndSignInUser(senderEmail, "Sender 4")
        val senderAttempt = firestoreAdapter.verifyRecipientUnlock(capsuleId, senderUid)
        assertTrue(senderAttempt is OperationResult.Denied)

        // Outsider attempting recipient unlock -> denied
        createAndSignInUser(outsiderEmail, "Outsider 4")
        val outsiderAttempt = firestoreAdapter.verifyRecipientUnlock(capsuleId, recipientUid)
        assertTrue(outsiderAttempt is OperationResult.Denied)
    }

    @Test
    fun test5_metadataMutationDuringUnlockDenied() = runBlocking {
        val testId = UUID.randomUUID().toString().take(8)
        val senderEmail = "sender_t5_$testId@example.com"
        val recipientEmail = "recipient_t5_$testId@example.com"
        val capsuleId = "capsule-t5-$testId"

        val senderUid = createAndSignInUser(senderEmail, "Sender 5")
        val recipientUid = createAndSignInUser(recipientEmail, "Recipient 5")

        seedTestCapsule(capsuleId, senderUid, senderEmail, "Sender 5", recipientUid, recipientEmail, "Recipient 5", futureUnlockSeconds = 3L)

        delay(3500L)

        createAndSignInUser(recipientEmail, "Recipient 5")

        try {
            firestore.collection("capsules").document(capsuleId).update(mapOf(
                "unlocked" to true,
                "unlockedAt" to FieldValue.serverTimestamp(),
                "title" to "Forged Title Change"
            )).await()
            fail("Mutating metadata during unlock must be denied by firestore.rules")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
    }

    @Test
    fun test6_protectedContentAccessDeniedBeforeUnlockAndAllowedAfter() = runBlocking {
        val testId = UUID.randomUUID().toString().take(8)
        val senderEmail = "sender_t6_$testId@example.com"
        val recipientEmail = "recipient_t6_$testId@example.com"
        val capsuleId = "capsule-t6-$testId"

        val senderUid = createAndSignInUser(senderEmail, "Sender 6")
        val recipientUid = createAndSignInUser(recipientEmail, "Recipient 6")

        seedTestCapsule(capsuleId, senderUid, senderEmail, "Sender 6", recipientUid, recipientEmail, "Recipient 6", futureUnlockSeconds = 3L)

        // 1. Recipient attempts protectedContent read BEFORE unlock -> denied
        createAndSignInUser(recipientEmail, "Recipient 6")
        try {
            firestore.collection("protectedContent").document(capsuleId).get().await()
            fail("Protected content read before unlock must be denied by firestore.rules")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }

        // 2. Wait for deadline and perform recipient unlock
        delay(3500L)
        val unlockRes = firestoreAdapter.verifyRecipientUnlock(capsuleId, recipientUid)
        assertTrue("Recipient unlock must succeed after deadline", unlockRes is OperationResult.Success)

        // 3. Recipient reads protectedContent AFTER unlock -> succeeds
        val protectedDoc = firestore.collection("protectedContent").document(capsuleId).get().await()
        assertTrue(protectedDoc.exists())
        assertEquals("Protected message text", protectedDoc.getString("text"))
    }

    @Test
    fun test7_unauthenticatedUnlockRequestFailsClosed() = runBlocking {
        auth.signOut()
        val result = firestoreAdapter.verifyRecipientUnlock("missing-capsule-id", "missing-recipient-uid")
        assertTrue("Unauthenticated unlock request must fail closed", result !is OperationResult.Success)
    }
}
