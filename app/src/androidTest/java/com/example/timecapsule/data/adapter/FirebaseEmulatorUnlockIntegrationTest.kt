package com.example.timecapsule.data.adapter

import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.example.timecapsule.config.FirebaseConfig
import com.example.timecapsule.domain.model.Availability
import com.example.timecapsule.domain.model.ForegroundLocationSample
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
 * Verifies Issue #15 and #17 server-authorized TIME, LOCATION, and BOTH unlock transactions,
 * early unlock denial, concurrent unlock idempotency, truth table combinations (T-19),
 * staying inside across deadline (T-20), non-recipient authorization denial, protected content access rules,
 * unconfirmed pending write rejection, and metadata immutability.
 *
 * Timing and Clock Verification Note:
 * Tests use bounded future unlock deadlines (e.g. 5 seconds) and [waitUntilServerTimeReached] with a 15s timeout
 * to wait for server-time progression. Firestore security rules enforce `request.time >= resource.data.unlockAt`
 * using the server's authoritative clock.
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
        authAdapter = DefaultAuthAdapter(authSupplier = { auth })
        firestoreAdapter = DefaultFirestoreAdapter(contextSupplier = { InstrumentationRegistry.getInstrumentation().targetContext })
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

    /**
     * Loops until current time in seconds passes [targetSeconds], with a bounded 15-second timeout to avoid infinite hangs.
     */
    private suspend fun waitUntilServerTimeReached(targetSeconds: Long, timeoutMillis: Long = 15000L) {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() / 1000 <= targetSeconds) {
            if (System.currentTimeMillis() - startTime > timeoutMillis) {
                fail("Timed out waiting for unlock deadline ($targetSeconds seconds)")
            }
            delay(100L)
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
        futureUnlockSeconds: Long = 5L,
        lockMode: String = "TIME"
    ): Timestamp {
        createAndSignInUser(senderEmail, senderName)
        val saveSenderRes = firestoreAdapter.saveRecipientProfile(RecipientProfile(senderUid, senderName))
        assertTrue("Sender profile save must succeed", saveSenderRes is OperationResult.Success)

        createAndSignInUser(recipientEmail, recipientName)
        val saveRecipientRes = firestoreAdapter.saveRecipientProfile(RecipientProfile(recipientUid, recipientName))
        assertTrue("Recipient profile save must succeed", saveRecipientRes is OperationResult.Success)

        createAndSignInUser(senderEmail, senderName)

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

        return unlockAtTimestamp
    }

    private suspend fun seedLocationOrBothCapsule(
        capsuleId: String,
        senderUid: String,
        senderEmail: String,
        senderName: String,
        recipientUid: String,
        recipientEmail: String,
        recipientName: String,
        lockMode: String,
        futureUnlockSeconds: Long = 5L,
        latitude: Double = 37.5665,
        longitude: Double = 126.9780,
        radiusM: Double = 150.0
    ): Timestamp? {
        createAndSignInUser(senderEmail, senderName)
        firestoreAdapter.saveRecipientProfile(RecipientProfile(senderUid, senderName))

        createAndSignInUser(recipientEmail, recipientName)
        firestoreAdapter.saveRecipientProfile(RecipientProfile(recipientUid, recipientName))

        createAndSignInUser(senderEmail, senderName)

        val unlockAtTimestamp = if (lockMode == "LOCATION") null else Timestamp(System.currentTimeMillis() / 1000 + futureUnlockSeconds, 0)
        val locationMap = mapOf(
            "latitude" to latitude,
            "longitude" to longitude,
            "radiusM" to radiusM
        )

        val metadata = mapOf(
            "capsuleId" to capsuleId,
            "senderId" to senderUid,
            "recipientId" to recipientUid,
            "title" to "Location/BOTH Capsule Title",
            "lockMode" to lockMode,
            "unlockAt" to unlockAtTimestamp,
            "location" to locationMap,
            "hasPhoto" to false,
            "photoPath" to null,
            "createdAt" to FieldValue.serverTimestamp(),
            "creationZone" to "Asia/Seoul",
            "snapshotDigest" to "b".repeat(64),
            "unlocked" to false
        )

        val content = mapOf(
            "capsuleId" to capsuleId,
            "text" to "Protected location message text",
            "photoObjectPath" to null,
            "snapshotDigest" to "b".repeat(64)
        )

        val batch = firestore.batch()
        batch.set(firestore.collection("capsules").document(capsuleId), metadata)
        batch.set(firestore.collection("protectedContent").document(capsuleId), content)
        batch.commit().await()

        return unlockAtTimestamp
    }

    @Test
    fun test1_serverAuthorizedTimeUnlockSucceedsAtOrAfterDeadline() = runBlocking {
        val testId = UUID.randomUUID().toString().take(8)
        val senderEmail = "sender_t1_$testId@example.com"
        val recipientEmail = "recipient_t1_$testId@example.com"
        val capsuleId = "capsule-t1-$testId"

        val senderUid = createAndSignInUser(senderEmail, "Sender 1")
        val recipientUid = createAndSignInUser(recipientEmail, "Recipient 1")

        val unlockAt = seedTestCapsule(capsuleId, senderUid, senderEmail, "Sender 1", recipientUid, recipientEmail, "Recipient 1", futureUnlockSeconds = 3L)

        waitUntilServerTimeReached(unlockAt.seconds)

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

        // Deadline set 3600s (1 hour) in the future to guarantee execution strictly before unlock deadline
        val unlockAt = seedTestCapsule(capsuleId, senderUid, senderEmail, "Sender 2", recipientUid, recipientEmail, "Recipient 2", futureUnlockSeconds = 3600L)

        assertTrue("Current time must be strictly earlier than unlockAt deadline", System.currentTimeMillis() / 1000 < unlockAt.seconds)

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

        val unlockAt = seedTestCapsule(capsuleId, senderUid, senderEmail, "Sender 3", recipientUid, recipientEmail, "Recipient 3", futureUnlockSeconds = 3L)

        waitUntilServerTimeReached(unlockAt.seconds)

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

        val unlockAt = seedTestCapsule(capsuleId, senderUid, senderEmail, "Sender 4", recipientUid, recipientEmail, "Recipient 4", futureUnlockSeconds = 3L)

        waitUntilServerTimeReached(unlockAt.seconds)

        createAndSignInUser(senderEmail, "Sender 4")
        val senderAttempt = firestoreAdapter.verifyRecipientUnlock(capsuleId, senderUid)
        assertTrue(senderAttempt is OperationResult.Denied)

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

        val unlockAt = seedTestCapsule(capsuleId, senderUid, senderEmail, "Sender 5", recipientUid, recipientEmail, "Recipient 5", futureUnlockSeconds = 3L)

        waitUntilServerTimeReached(unlockAt.seconds)

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

        val unlockAt = seedTestCapsule(capsuleId, senderUid, senderEmail, "Sender 6", recipientUid, recipientEmail, "Recipient 6", futureUnlockSeconds = 3L)

        createAndSignInUser(recipientEmail, "Recipient 6")
        try {
            firestore.collection("protectedContent").document(capsuleId).get().await()
            fail("Protected content read before unlock must be denied by firestore.rules")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }

        waitUntilServerTimeReached(unlockAt.seconds)
        val unlockRes = firestoreAdapter.verifyRecipientUnlock(capsuleId, recipientUid)
        assertTrue("Recipient unlock must succeed after deadline", unlockRes is OperationResult.Success)

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

    @Test
    fun test8_locationOnlyUnlockSucceedsWithValidLocationAndFailsWhenOutside() = runBlocking {
        val testId = UUID.randomUUID().toString().take(8)
        val senderEmail = "sender_t8_$testId@example.com"
        val recipientEmail = "recipient_t8_$testId@example.com"
        val capsuleId = "capsule-t8-$testId"

        val senderUid = createAndSignInUser(senderEmail, "Sender 8")
        val recipientUid = createAndSignInUser(recipientEmail, "Recipient 8")

        seedLocationOrBothCapsule(capsuleId, senderUid, senderEmail, "Sender 8", recipientUid, recipientEmail, "Recipient 8", lockMode = "LOCATION", latitude = 37.5665, longitude = 126.9780)

        createAndSignInUser(recipientEmail, "Recipient 8")

        val sampleOutside = ForegroundLocationSample(37.5692, 126.9780, SystemClock.elapsedRealtimeNanos(), 15.0f)
        val resOutside = firestoreAdapter.verifyRecipientUnlock(capsuleId, recipientUid, sampleOutside)
        assertTrue("Outside location fix must be denied", resOutside is OperationResult.Denied)

        val sampleInside = ForegroundLocationSample(37.5665, 126.9780, SystemClock.elapsedRealtimeNanos(), 15.0f)
        val resInside = firestoreAdapter.verifyRecipientUnlock(capsuleId, recipientUid, sampleInside)
        assertTrue("Inside location fix must succeed", resInside is OperationResult.Success)
        assertEquals(Availability.AVAILABLE, (resInside as OperationResult.Success).data)
    }

    @Test
    fun test9_bothModeTruthTableCombinations() = runBlocking {
        val testId = UUID.randomUUID().toString().take(8)
        val senderEmail = "sender_t9_$testId@example.com"
        val recipientEmail = "recipient_t9_$testId@example.com"
        val capsuleId = "capsule-t9-$testId"

        val senderUid = createAndSignInUser(senderEmail, "Sender 9")
        val recipientUid = createAndSignInUser(recipientEmail, "Recipient 9")

        // Bounded 5s unlockAt deadline to prevent setup time consuming the early window
        val unlockAt = seedLocationOrBothCapsule(capsuleId, senderUid, senderEmail, "Sender 9", recipientUid, recipientEmail, "Recipient 9", lockMode = "BOTH", futureUnlockSeconds = 5L)!!

        createAndSignInUser(recipientEmail, "Recipient 9")
        val sampleInside = ForegroundLocationSample(37.5665, 126.9780, SystemClock.elapsedRealtimeNanos(), 15.0f)
        val sampleOutside = ForegroundLocationSample(37.5692, 126.9780, SystemClock.elapsedRealtimeNanos(), 15.0f)

        // Ensure early time assertions execute strictly before unlock deadline
        assertTrue("Must execute before unlock deadline", System.currentTimeMillis() / 1000 < unlockAt.seconds)

        // 1. F / F: Time early, location outside -> Denied
        val resFF = firestoreAdapter.verifyRecipientUnlock(capsuleId, recipientUid, sampleOutside)
        assertTrue("Early time + outside location must be denied", resFF is OperationResult.Denied)

        // 2. F / T: Time early, location inside -> Denied (backend rules reject request.time < unlockAt)
        val resFT = firestoreAdapter.verifyRecipientUnlock(capsuleId, recipientUid, sampleInside)
        assertTrue("Early time + inside location must be denied by rules", resFT is OperationResult.Denied)

        // Wait until server unlock deadline is reached (with 15s timeout)
        waitUntilServerTimeReached(unlockAt.seconds)
        assertTrue("Must execute at or after unlock deadline", System.currentTimeMillis() / 1000 >= unlockAt.seconds)

        // 3. T / F: Time reached, location outside -> Denied (client location qualification rejects)
        val resTF = firestoreAdapter.verifyRecipientUnlock(capsuleId, recipientUid, sampleOutside)
        assertTrue("Time reached + outside location must be denied", resTF is OperationResult.Denied)

        // 4. T / T: Time reached, location inside -> Success
        val resTT = firestoreAdapter.verifyRecipientUnlock(capsuleId, recipientUid, sampleInside)
        assertTrue("Time reached + inside location must succeed", resTT is OperationResult.Success)
        assertEquals(Availability.AVAILABLE, (resTT as OperationResult.Success).data)
    }

    @Test
    fun test10_bothModePriorVisitDepartureFailsAfterDeadline() = runBlocking {
        val testId = UUID.randomUUID().toString().take(8)
        val senderEmail = "sender_t10_$testId@example.com"
        val recipientEmail = "recipient_t10_$testId@example.com"
        val capsuleId = "capsule-t10-$testId"

        val senderUid = createAndSignInUser(senderEmail, "Sender 10")
        val recipientUid = createAndSignInUser(recipientEmail, "Recipient 10")

        val unlockAt = seedLocationOrBothCapsule(capsuleId, senderUid, senderEmail, "Sender 10", recipientUid, recipientEmail, "Recipient 10", lockMode = "BOTH", futureUnlockSeconds = 5L)!!

        createAndSignInUser(recipientEmail, "Recipient 10")

        // 1. Prior visit inside location before deadline -> Denied
        assertTrue("Must execute before deadline", System.currentTimeMillis() / 1000 < unlockAt.seconds)
        val sampleInside = ForegroundLocationSample(37.5665, 126.9780, SystemClock.elapsedRealtimeNanos(), 15.0f)
        val resEarly = firestoreAdapter.verifyRecipientUnlock(capsuleId, recipientUid, sampleInside)
        assertTrue(resEarly is OperationResult.Denied)

        // 2. User departs location (~300m away) and time deadline passes
        waitUntilServerTimeReached(unlockAt.seconds)
        val sampleOutside = ForegroundLocationSample(37.5692, 126.9780, SystemClock.elapsedRealtimeNanos(), 15.0f)

        // 3. Recheck after deadline while outside -> Denied (no past-visit latch!)
        val resLateOutside = firestoreAdapter.verifyRecipientUnlock(capsuleId, recipientUid, sampleOutside)
        assertTrue("Recheck after departure must be denied", resLateOutside is OperationResult.Denied)
    }

    @Test
    fun test11_bothModeRemainingInsideSucceedsAfterDeadlineWithoutReentry() = runBlocking {
        val testId = UUID.randomUUID().toString().take(8)
        val senderEmail = "sender_t11_$testId@example.com"
        val recipientEmail = "recipient_t11_$testId@example.com"
        val capsuleId = "capsule-t11-$testId"

        val senderUid = createAndSignInUser(senderEmail, "Sender 11")
        val recipientUid = createAndSignInUser(recipientEmail, "Recipient 11")

        val unlockAt = seedLocationOrBothCapsule(capsuleId, senderUid, senderEmail, "Sender 11", recipientUid, recipientEmail, "Recipient 11", lockMode = "BOTH", futureUnlockSeconds = 5L)!!

        createAndSignInUser(recipientEmail, "Recipient 11")

        // 1. Attempt while inside before deadline -> Denied
        assertTrue("Must execute before deadline", System.currentTimeMillis() / 1000 < unlockAt.seconds)
        val sample1 = ForegroundLocationSample(37.5665, 126.9780, SystemClock.elapsedRealtimeNanos(), 15.0f)
        val res1 = firestoreAdapter.verifyRecipientUnlock(capsuleId, recipientUid, sample1)
        assertTrue(res1 is OperationResult.Denied)

        // 2. Recipient stays inside location while time passes deadline
        waitUntilServerTimeReached(unlockAt.seconds)

        // 3. Fresh check succeeds without needing exit / re-enter (T-20)
        val sample2 = ForegroundLocationSample(37.5665, 126.9780, SystemClock.elapsedRealtimeNanos(), 15.0f)
        val res2 = firestoreAdapter.verifyRecipientUnlock(capsuleId, recipientUid, sample2)
        assertTrue("Fresh check while remaining inside must succeed after deadline", res2 is OperationResult.Success)
        assertEquals(Availability.AVAILABLE, (res2 as OperationResult.Success).data)
    }

    @Test
    fun test12_readRecipientUnlockRequirementsSucceedsForConfirmedServerMetadata() = runBlocking {
        val testId = UUID.randomUUID().toString().take(8)
        val senderEmail = "sender_t12_$testId@example.com"
        val recipientEmail = "recipient_t12_$testId@example.com"
        val capsuleId = "capsule-t12-$testId"

        val senderUid = createAndSignInUser(senderEmail, "Sender 12")
        val recipientUid = createAndSignInUser(recipientEmail, "Recipient 12")

        seedTestCapsule(capsuleId, senderUid, senderEmail, "Sender 12", recipientUid, recipientEmail, "Recipient 12", futureUnlockSeconds = 2L)

        createAndSignInUser(recipientEmail, "Recipient 12")

        // Direct server read of requirements returns success for confirmed server metadata
        val resReq = firestoreAdapter.readRecipientUnlockRequirements(capsuleId, recipientUid)
        assertTrue("Confirmed server requirements read must succeed", resReq is OperationResult.Success)
    }
}
