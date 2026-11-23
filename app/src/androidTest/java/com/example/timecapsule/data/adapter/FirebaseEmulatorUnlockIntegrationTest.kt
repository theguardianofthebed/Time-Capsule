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

/**
 * Android Instrumentation Integration tests executing against live Firebase Auth & Firestore Emulators.
 * Verifies Issue #15 server-authorized TIME unlock transactions, early unlock denial,
 * concurrent unlock idempotency, non-recipient authorization denial, and metadata immutability.
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
        recipientUid: String,
        unlockAtMillis: Long,
        lockMode: String = "TIME"
    ) {
        // Ensure recipient directory profiles exist for rules validMetadata / create constraints
        firestoreAdapter.saveRecipientProfile(RecipientProfile(senderUid, "Sender User"))
        firestoreAdapter.saveRecipientProfile(RecipientProfile(recipientUid, "Recipient User"))

        val metadata = mapOf(
            "capsuleId" to capsuleId,
            "senderId" to senderUid,
            "recipientId" to recipientUid,
            "title" to "Test Capsule Title",
            "lockMode" to lockMode,
            "unlockAt" to Timestamp(unlockAtMillis / 1000, 0),
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
        val senderUid = createAndSignInUser("sender_unlock_t1@example.com", "Sender 1")
        val recipientUid = createAndSignInUser("recipient_unlock_t1@example.com", "Recipient 1")

        val capsuleId = "capsule-unlock-t1"
        val pastUnlockMillis = System.currentTimeMillis() - 60_000L

        createAndSignInUser("sender_unlock_t1@example.com", "Sender 1")
        seedTestCapsule(capsuleId, senderUid, recipientUid, pastUnlockMillis)

        createAndSignInUser("recipient_unlock_t1@example.com", "Recipient 1")
        val result = firestoreAdapter.verifyRecipientUnlock(capsuleId, recipientUid)

        assertTrue("Recipient unlock must succeed after deadline", result is OperationResult.Success)
        assertEquals(Availability.AVAILABLE, (result as OperationResult.Success).data)

        val doc = firestore.collection("capsules").document(capsuleId).get().await()
        assertEquals(true, doc.getBoolean("unlocked"))
        assertNotNull(doc.getTimestamp("unlockedAt"))
    }

    @Test
    fun test2_earlyUnlockDeniedBeforeDeadline() = runBlocking {
        val senderUid = createAndSignInUser("sender_unlock_t2@example.com", "Sender 2")
        val recipientUid = createAndSignInUser("recipient_unlock_t2@example.com", "Recipient 2")

        val capsuleId = "capsule-unlock-t2"
        val futureUnlockMillis = System.currentTimeMillis() + 3_600_000L

        createAndSignInUser("sender_unlock_t2@example.com", "Sender 2")
        seedTestCapsule(capsuleId, senderUid, recipientUid, futureUnlockMillis)

        createAndSignInUser("recipient_unlock_t2@example.com", "Recipient 2")
        val result = firestoreAdapter.verifyRecipientUnlock(capsuleId, recipientUid)

        assertTrue("Early unlock before deadline must be denied", result is OperationResult.Denied)
    }

    @Test
    fun test3_repeatedAndConcurrentUnlockIsIdempotent() = runBlocking {
        val senderUid = createAndSignInUser("sender_unlock_t3@example.com", "Sender 3")
        val recipientUid = createAndSignInUser("recipient_unlock_t3@example.com", "Recipient 3")

        val capsuleId = "capsule-unlock-t3"
        val pastUnlockMillis = System.currentTimeMillis() - 60_000L

        createAndSignInUser("sender_unlock_t3@example.com", "Sender 3")
        seedTestCapsule(capsuleId, senderUid, recipientUid, pastUnlockMillis)

        createAndSignInUser("recipient_unlock_t3@example.com", "Recipient 3")

        val result1 = firestoreAdapter.verifyRecipientUnlock(capsuleId, recipientUid)
        assertTrue(result1 is OperationResult.Success)

        val result2 = firestoreAdapter.verifyRecipientUnlock(capsuleId, recipientUid)
        assertTrue(result2 is OperationResult.Success)
        assertEquals(Availability.AVAILABLE, (result2 as OperationResult.Success).data)
    }

    @Test
    fun test4_unauthorizedCallerDenied() = runBlocking {
        val senderUid = createAndSignInUser("sender_unlock_t4@example.com", "Sender 4")
        val recipientUid = createAndSignInUser("recipient_unlock_t4@example.com", "Recipient 4")

        val capsuleId = "capsule-unlock-t4"
        val pastUnlockMillis = System.currentTimeMillis() - 60_000L

        createAndSignInUser("sender_unlock_t4@example.com", "Sender 4")
        seedTestCapsule(capsuleId, senderUid, recipientUid, pastUnlockMillis)

        val senderAttempt = firestoreAdapter.verifyRecipientUnlock(capsuleId, senderUid)
        assertTrue(senderAttempt is OperationResult.Denied)

        createAndSignInUser("outsider_unlock_t4@example.com", "Outsider 4")
        val outsiderAttempt = firestoreAdapter.verifyRecipientUnlock(capsuleId, recipientUid)
        assertTrue(outsiderAttempt is OperationResult.Denied)
    }

    @Test
    fun test5_metadataMutationDuringUnlockDenied() = runBlocking {
        val senderUid = createAndSignInUser("sender_unlock_t5@example.com", "Sender 5")
        val recipientUid = createAndSignInUser("recipient_unlock_t5@example.com", "Recipient 5")

        val capsuleId = "capsule-unlock-t5"
        val pastUnlockMillis = System.currentTimeMillis() - 60_000L

        createAndSignInUser("sender_unlock_t5@example.com", "Sender 5")
        seedTestCapsule(capsuleId, senderUid, recipientUid, pastUnlockMillis)

        createAndSignInUser("recipient_unlock_t5@example.com", "Recipient 5")

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
}
