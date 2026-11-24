package com.example.timecapsule.data

import com.example.timecapsule.data.adapter.AuthAdapter
import com.example.timecapsule.data.adapter.FirestoreAdapter
import com.example.timecapsule.data.adapter.LocationAdapter
import com.example.timecapsule.data.session.DefaultSessionTracker
import com.example.timecapsule.domain.location.LocationQualifier
import com.example.timecapsule.domain.model.Availability
import com.example.timecapsule.domain.model.ForegroundLocationSample
import com.example.timecapsule.domain.model.LocationSampleResult
import com.example.timecapsule.domain.model.LocationSampleStatus
import com.example.timecapsule.domain.model.LocationTarget
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.domain.model.QualificationFailureReason
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.model.RecipientUnlockRequirements
import com.example.timecapsule.domain.result.OperationResult
import com.google.firebase.firestore.FirebaseFirestore
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM Unit tests verifying [DefaultCapsuleRepository.verifyUnlock] behavior for Issue #17.
 * Covers TIME, LOCATION, and BOTH lock modes, live recipient requirements reads, current-location qualification,
 * truth table simulation (T-19), staying inside before/after deadline (T-20), boundary enforcement (T-42),
 * missing permissions/services, session race protection, pending-write rejection, and idempotent unlocked state.
 */
class DefaultCapsuleRepositoryUnlockTest {

    private lateinit var sessionTracker: DefaultSessionTracker
    private lateinit var fakeAuthAdapter: FakeAuthAdapterForUnlock
    private lateinit var fakeFirestoreAdapter: FakeFirestoreAdapterForUnlock
    private lateinit var fakeLocationAdapter: FakeLocationAdapterForUnlock
    private lateinit var repository: DefaultCapsuleRepository

    private val targetSeoul = LocationTarget(latitude = 37.5665, longitude = 126.9780, radiusMeters = 150.0)
    private var testNowNanos = 100_000_000_000L

    @Before
    fun setUp() {
        testNowNanos = 100_000_000_000L
        sessionTracker = DefaultSessionTracker()
        fakeAuthAdapter = FakeAuthAdapterForUnlock(sessionTracker)
        fakeFirestoreAdapter = FakeFirestoreAdapterForUnlock()
        fakeLocationAdapter = FakeLocationAdapterForUnlock()
        repository = DefaultCapsuleRepository(
            sessionTracker = sessionTracker,
            authAdapter = fakeAuthAdapter,
            firestoreAdapter = fakeFirestoreAdapter,
            locationAdapter = fakeLocationAdapter,
            clockNanosSupplier = { testNowNanos }
        )
    }

    @Test
    fun `verifyUnlock rejects unauthenticated sessions`() = runBlocking {
        sessionTracker.clearSession()

        val result = repository.verifyUnlock("capsule-100")

        assertTrue(result is OperationResult.Denied)
        val message = (result as OperationResult.Denied).message
        assertTrue(message.contains("Authentication required"))
    }

    @Test
    fun `verifyUnlock rejects blank capsule ID`() = runBlocking {
        sessionTracker.startSession("recipient-uid-1")

        val result = repository.verifyUnlock("   ")

        assertTrue(result is OperationResult.Invalid)
    }

    @Test
    fun `verifyUnlock denies non recipient unlock attempts`() = runBlocking {
        sessionTracker.startSession("third-party-uid")
        fakeFirestoreAdapter.seedCapsule(
            capsuleId = "capsule-101",
            recipientUid = "recipient-uid-1",
            unlocked = false,
            lockMode = "TIME",
            deadlineReached = true
        )

        val result = repository.verifyUnlock("capsule-101")

        assertTrue(result is OperationResult.Denied)
        val message = (result as OperationResult.Denied).message
        assertTrue(message.contains("Only the designated recipient"))
    }

    @Test
    fun `verifyUnlock denies early TIME unlock before server deadline`() = runBlocking {
        sessionTracker.startSession("recipient-uid-1")
        fakeFirestoreAdapter.seedCapsule(
            capsuleId = "capsule-102",
            recipientUid = "recipient-uid-1",
            unlocked = false,
            lockMode = "TIME",
            deadlineReached = false
        )

        val result = repository.verifyUnlock("capsule-102")

        assertTrue(result is OperationResult.Denied)
        val message = (result as OperationResult.Denied).message
        assertTrue(message.contains("unlock time not reached") || message.contains("denied"))
    }

    @Test
    fun `verifyUnlock succeeds for TIME recipient at or after server deadline`() = runBlocking {
        sessionTracker.startSession("recipient-uid-1")
        fakeFirestoreAdapter.seedCapsule(
            capsuleId = "capsule-103",
            recipientUid = "recipient-uid-1",
            unlocked = false,
            lockMode = "TIME",
            deadlineReached = true
        )

        val result = repository.verifyUnlock("capsule-103")

        assertTrue(result is OperationResult.Success)
        assertEquals(Availability.AVAILABLE, (result as OperationResult.Success).data)
        assertTrue(fakeFirestoreAdapter.isCapsuleUnlocked("capsule-103"))
    }

    @Test
    fun `verifyUnlock rejects unacknowledged local update with pending writes`() = runBlocking {
        sessionTracker.startSession("recipient-uid-1")
        fakeFirestoreAdapter.seedCapsule(
            capsuleId = "pending-writes-capsule",
            recipientUid = "recipient-uid-1",
            unlocked = true, // Unconfirmed local update
            lockMode = "LOCATION",
            deadlineReached = true,
            locationTarget = targetSeoul,
            hasPendingWrites = true // P0: Local pending write flag!
        )

        val result = repository.verifyUnlock("pending-writes-capsule")

        // P0: Must reject pending write snapshot in readRecipientUnlockRequirements and return Unavailable
        assertTrue(result is OperationResult.Unavailable)
        assertTrue((result as OperationResult.Unavailable).message.contains("unconfirmed") || result.message.contains("cached"))
    }

    @Test
    fun `verifyUnlock treats already unlocked capsule as idempotent success without querying GPS`() = runBlocking {
        sessionTracker.startSession("recipient-uid-1")
        fakeFirestoreAdapter.seedCapsule(
            capsuleId = "capsule-104",
            recipientUid = "recipient-uid-1",
            unlocked = true,
            lockMode = "LOCATION",
            deadlineReached = true,
            locationTarget = targetSeoul,
            hasPendingWrites = false
        )

        fakeLocationAdapter.fineLocationGranted = false // Would fail if GPS were queried

        val result = repository.verifyUnlock("capsule-104")
        assertTrue(result is OperationResult.Success)
        assertEquals(Availability.AVAILABLE, (result as OperationResult.Success).data)
    }

    @Test
    fun `verifyUnlock succeeds for LOCATION mode when fresh sample is within 150m`() = runBlocking {
        sessionTracker.startSession("recipient-uid-1")
        fakeFirestoreAdapter.seedCapsule(
            capsuleId = "capsule-loc-1",
            recipientUid = "recipient-uid-1",
            unlocked = false,
            lockMode = "LOCATION",
            deadlineReached = true,
            locationTarget = targetSeoul
        )
        fakeLocationAdapter.sampleResult = LocationSampleResult(
            status = LocationSampleStatus.SUCCESS,
            sample = ForegroundLocationSample(
                latitude = 37.5665,
                longitude = 126.9780,
                elapsedRealtimeNanos = 90_000_000_000L, // 10s old relative to 100s
                horizontalAccuracyMeters = 20.0f
            )
        )

        val result = repository.verifyUnlock("capsule-loc-1")

        assertTrue(result is OperationResult.Success)
        assertEquals(Availability.AVAILABLE, (result as OperationResult.Success).data)
        assertTrue(fakeFirestoreAdapter.isCapsuleUnlocked("capsule-loc-1"))
    }

    @Test
    fun `verifyUnlock denies LOCATION mode when sample is outside 150m radius`() = runBlocking {
        sessionTracker.startSession("recipient-uid-1")
        fakeFirestoreAdapter.seedCapsule(
            capsuleId = "capsule-loc-2",
            recipientUid = "recipient-uid-1",
            unlocked = false,
            lockMode = "LOCATION",
            deadlineReached = true,
            locationTarget = targetSeoul
        )
        fakeLocationAdapter.sampleResult = LocationSampleResult(
            status = LocationSampleStatus.SUCCESS,
            sample = ForegroundLocationSample(
                latitude = 37.5692, // ~300m away
                longitude = 126.9780,
                elapsedRealtimeNanos = 90_000_000_000L,
                horizontalAccuracyMeters = 20.0f
            )
        )

        val result = repository.verifyUnlock("capsule-loc-2")

        assertTrue(result is OperationResult.Denied)
        val message = (result as OperationResult.Denied).message
        assertTrue(message.contains("outside target radius"))
        assertFalse(fakeFirestoreAdapter.isCapsuleUnlocked("capsule-loc-2"))
    }

    @Test
    fun `verifyUnlock denies LOCATION mode when permission is revoked prior to submission`() = runBlocking {
        sessionTracker.startSession("recipient-uid-1")
        fakeFirestoreAdapter.seedCapsule(
            capsuleId = "capsule-loc-perm-revoked",
            recipientUid = "recipient-uid-1",
            unlocked = false,
            lockMode = "LOCATION",
            deadlineReached = true,
            locationTarget = targetSeoul
        )

        fakeLocationAdapter.fineLocationGranted = true
        fakeLocationAdapter.sampleResult = LocationSampleResult(
            status = LocationSampleStatus.SUCCESS,
            sample = ForegroundLocationSample(37.5665, 126.9780, 90_000_000_000L, 20.0f)
        )
        fakeLocationAdapter.onGetLocationCallback = {
            fakeLocationAdapter.fineLocationGranted = false
        }

        val result = repository.verifyUnlock("capsule-loc-perm-revoked")

        assertTrue(result is OperationResult.Denied)
        assertTrue((result as OperationResult.Denied).message.contains("permission was revoked"))
    }

    @Test
    fun `verifyUnlock denies LOCATION mode when location services are disabled prior to submission`() = runBlocking {
        sessionTracker.startSession("recipient-uid-1")
        fakeFirestoreAdapter.seedCapsule(
            capsuleId = "capsule-loc-service-off",
            recipientUid = "recipient-uid-1",
            unlocked = false,
            lockMode = "LOCATION",
            deadlineReached = true,
            locationTarget = targetSeoul
        )

        fakeLocationAdapter.locationAvailable = true
        fakeLocationAdapter.sampleResult = LocationSampleResult(
            status = LocationSampleStatus.SUCCESS,
            sample = ForegroundLocationSample(37.5665, 126.9780, 90_000_000_000L, 20.0f)
        )
        fakeLocationAdapter.onGetLocationCallback = {
            fakeLocationAdapter.locationAvailable = false
        }

        val result = repository.verifyUnlock("capsule-loc-service-off")

        assertTrue(result is OperationResult.Unavailable)
        assertTrue((result as OperationResult.Unavailable).message.contains("disabled"))
    }

    /**
     * Verifies that the transaction callback re-evaluates the clock supplier on every transaction attempt.
     *
     * Note: This test simulates time advancement during callback execution on a fake adapter to verify
     * callback freshness rechecking. It is a unit verification of callback logic, not proof of SDK-level
     * Firestore contention retry mechanics.
     */
    @Test
    fun `verifyUnlockTransactionCallback_rechecksMonotonicClockAndRejectsExpiredSample`() = runBlocking {
        sessionTracker.startSession("recipient-uid-1")
        fakeFirestoreAdapter.seedCapsule(
            capsuleId = "capsule-loc-expiry-retry",
            recipientUid = "recipient-uid-1",
            unlocked = false,
            lockMode = "LOCATION",
            deadlineReached = true,
            locationTarget = targetSeoul
        )

        fakeLocationAdapter.sampleResult = LocationSampleResult(
            status = LocationSampleStatus.SUCCESS,
            sample = ForegroundLocationSample(
                latitude = 37.5665,
                longitude = 126.9780,
                elapsedRealtimeNanos = 90_000_000_000L, // Fresh when testNowNanos=100s (age=10s)
                horizontalAccuracyMeters = 20.0f
            )
        )

        // Simulate clock advancing by 60s during transaction retry execution
        fakeFirestoreAdapter.onTransactionRetryCallback = {
            testNowNanos = 160_000_000_001L // Age becomes 70.000000001s > 60s!
        }

        val result = repository.verifyUnlock("capsule-loc-expiry-retry")

        assertTrue(result is OperationResult.Unavailable)
        assertTrue((result as OperationResult.Unavailable).message.contains("stale"))
        assertFalse(fakeFirestoreAdapter.isCapsuleUnlocked("capsule-loc-expiry-retry"))
    }

    @Test
    fun `verifyUnlock rejects malformed location record missing radius`() = runBlocking {
        sessionTracker.startSession("recipient-uid-1")
        fakeFirestoreAdapter.seedCapsule(
            capsuleId = "capsule-malformed-radius",
            recipientUid = "recipient-uid-1",
            unlocked = false,
            lockMode = "LOCATION",
            deadlineReached = true,
            locationTarget = null,
            isMalformedLocationTarget = true
        )

        val result = repository.verifyUnlock("capsule-malformed-radius")

        assertTrue(result is OperationResult.Invalid)
        assertTrue((result as OperationResult.Invalid).message.contains("malformed") || result.message.contains("requires a target"))
    }

    @Test
    fun `verifyUnlock tests BOTH mode truth table combinations (T-19)`() = runBlocking {
        sessionTracker.startSession("recipient-uid-1")

        // 1. Early time + Outside location => Denied
        fakeFirestoreAdapter.seedCapsule(
            capsuleId = "both-early-outside",
            recipientUid = "recipient-uid-1",
            unlocked = false,
            lockMode = "BOTH",
            deadlineReached = false,
            locationTarget = targetSeoul
        )
        fakeLocationAdapter.sampleResult = LocationSampleResult(
            status = LocationSampleStatus.SUCCESS,
            sample = ForegroundLocationSample(37.5692, 126.9780, 90_000_000_000L, 20.0f)
        )
        val res1 = repository.verifyUnlock("both-early-outside")
        assertTrue(res1 is OperationResult.Denied)

        // 2. Early time + Inside location => Denied
        fakeFirestoreAdapter.seedCapsule(
            capsuleId = "both-early-inside",
            recipientUid = "recipient-uid-1",
            unlocked = false,
            lockMode = "BOTH",
            deadlineReached = false,
            locationTarget = targetSeoul
        )
        fakeLocationAdapter.sampleResult = LocationSampleResult(
            status = LocationSampleStatus.SUCCESS,
            sample = ForegroundLocationSample(37.5665, 126.9780, 90_000_000_000L, 20.0f)
        )
        val res2 = repository.verifyUnlock("both-early-inside")
        assertTrue(res2 is OperationResult.Denied)

        // 3. Valid time + Outside location => Denied
        fakeFirestoreAdapter.seedCapsule(
            capsuleId = "both-valid-outside",
            recipientUid = "recipient-uid-1",
            unlocked = false,
            lockMode = "BOTH",
            deadlineReached = true,
            locationTarget = targetSeoul
        )
        fakeLocationAdapter.sampleResult = LocationSampleResult(
            status = LocationSampleStatus.SUCCESS,
            sample = ForegroundLocationSample(37.5692, 126.9780, 90_000_000_000L, 20.0f)
        )
        val res3 = repository.verifyUnlock("both-valid-outside")
        assertTrue(res3 is OperationResult.Denied)

        // 4. Valid time + Inside location => Success (AVAILABLE)
        fakeFirestoreAdapter.seedCapsule(
            capsuleId = "both-valid-inside",
            recipientUid = "recipient-uid-1",
            unlocked = false,
            lockMode = "BOTH",
            deadlineReached = true,
            locationTarget = targetSeoul
        )
        fakeLocationAdapter.sampleResult = LocationSampleResult(
            status = LocationSampleStatus.SUCCESS,
            sample = ForegroundLocationSample(37.5665, 126.9780, 90_000_000_000L, 20.0f)
        )
        val res4 = repository.verifyUnlock("both-valid-inside")
        assertTrue(res4 is OperationResult.Success)
        assertEquals(Availability.AVAILABLE, (res4 as OperationResult.Success).data)
        assertTrue(fakeFirestoreAdapter.isCapsuleUnlocked("both-valid-inside"))
    }

    @Test
    fun `verifyUnlock handles prior location visit followed by departure before deadline`() = runBlocking {
        sessionTracker.startSession("recipient-uid-1")
        fakeFirestoreAdapter.seedCapsule(
            capsuleId = "visit-departure-capsule",
            recipientUid = "recipient-uid-1",
            unlocked = false,
            lockMode = "BOTH",
            deadlineReached = false,
            locationTarget = targetSeoul
        )

        // Early visit inside location -> denied due to early deadline
        fakeLocationAdapter.sampleResult = LocationSampleResult(
            status = LocationSampleStatus.SUCCESS,
            sample = ForegroundLocationSample(37.5665, 126.9780, 90_000_000_000L, 20.0f)
        )
        val attempt1 = repository.verifyUnlock("visit-departure-capsule")
        assertTrue(attempt1 is OperationResult.Denied)

        // User departs location (~300m away) and time deadline passes
        fakeFirestoreAdapter.seedCapsule(
            capsuleId = "visit-departure-capsule",
            recipientUid = "recipient-uid-1",
            unlocked = false,
            lockMode = "BOTH",
            deadlineReached = true,
            locationTarget = targetSeoul
        )
        fakeLocationAdapter.sampleResult = LocationSampleResult(
            status = LocationSampleStatus.SUCCESS,
            sample = ForegroundLocationSample(37.5692, 126.9780, 90_000_000_000L, 20.0f) // ~300m away
        )

        // Attempt 2 after deadline while outside => Denied (no past-visit latch!)
        val attempt2 = repository.verifyUnlock("visit-departure-capsule")
        assertTrue(attempt2 is OperationResult.Denied)
        assertFalse(fakeFirestoreAdapter.isCapsuleUnlocked("visit-departure-capsule"))
    }

    @Test
    fun `verifyUnlock handles recipient remaining inside before and after deadline without re-entry (T-20)`() = runBlocking {
        sessionTracker.startSession("recipient-uid-1")
        fakeFirestoreAdapter.seedCapsule(
            capsuleId = "t20-capsule",
            recipientUid = "recipient-uid-1",
            unlocked = false,
            lockMode = "BOTH",
            deadlineReached = false,
            locationTarget = targetSeoul
        )
        fakeLocationAdapter.sampleResult = LocationSampleResult(
            status = LocationSampleStatus.SUCCESS,
            sample = ForegroundLocationSample(37.5665, 126.9780, 90_000_000_000L, 20.0f)
        )

        // Attempt 1: Early deadline => Denied
        val attempt1 = repository.verifyUnlock("t20-capsule")
        assertTrue(attempt1 is OperationResult.Denied)
        assertFalse(fakeFirestoreAdapter.isCapsuleUnlocked("t20-capsule"))

        // Server time passes deadline while recipient stays inside location
        fakeFirestoreAdapter.seedCapsule(
            capsuleId = "t20-capsule",
            recipientUid = "recipient-uid-1",
            unlocked = false,
            lockMode = "BOTH",
            deadlineReached = true,
            locationTarget = targetSeoul
        )

        // Attempt 2: Fresh foreground fix succeeds without needing exit/re-enter (T-20)
        val attempt2 = repository.verifyUnlock("t20-capsule")
        assertTrue(attempt2 is OperationResult.Success)
        assertEquals(Availability.AVAILABLE, (attempt2 as OperationResult.Success).data)
        assertTrue(fakeFirestoreAdapter.isCapsuleUnlocked("t20-capsule"))
    }

    @Test
    fun `verifyUnlock lost transaction response reconciles committed and uncommitted outcomes`() = runBlocking {
        sessionTracker.startSession("recipient-uid-1")

        // Case A: Transaction committed on server before response was lost
        fakeFirestoreAdapter.seedCapsule(
            capsuleId = "lost-response-committed",
            recipientUid = "recipient-uid-1",
            unlocked = false,
            lockMode = "TIME",
            deadlineReached = true
        )
        fakeFirestoreAdapter.simulateLostResponseCommitted = true

        val resCommitted = repository.verifyUnlock("lost-response-committed")
        assertTrue(resCommitted is OperationResult.Success)
        assertEquals(Availability.AVAILABLE, (resCommitted as OperationResult.Success).data)

        // Case B: Transaction failed/uncommitted on server before response was lost
        fakeFirestoreAdapter.seedCapsule(
            capsuleId = "lost-response-uncommitted",
            recipientUid = "recipient-uid-1",
            unlocked = false,
            lockMode = "TIME",
            deadlineReached = true
        )
        fakeFirestoreAdapter.simulateLostResponseCommitted = false
        fakeFirestoreAdapter.simulateLostResponseError = true

        val resUncommitted = repository.verifyUnlock("lost-response-uncommitted")
        assertTrue(resUncommitted is OperationResult.Unavailable)
        assertFalse(fakeFirestoreAdapter.isCapsuleUnlocked("lost-response-uncommitted"))
    }

    @Test
    fun `verifyUnlock rejects result if account session changes during verification`() = runBlocking {
        sessionTracker.startSession("recipient-uid-1")
        fakeFirestoreAdapter.seedCapsule(
            capsuleId = "capsule-session-race",
            recipientUid = "recipient-uid-1",
            unlocked = false,
            lockMode = "TIME",
            deadlineReached = true
        )

        fakeFirestoreAdapter.onUnlockCallback = {
            sessionTracker.clearSession()
        }

        val result = repository.verifyUnlock("capsule-session-race")
        assertTrue(result is OperationResult.Denied)
        assertTrue((result as OperationResult.Denied).message.contains("session"))
    }

    @Test
    fun `verifyUnlock rejects result if same UID is relogged in mid flight`() = runBlocking {
        sessionTracker.startSession("recipient-uid-1")
        fakeFirestoreAdapter.seedCapsule(
            capsuleId = "capsule-same-uid-relogin",
            recipientUid = "recipient-uid-1",
            unlocked = false,
            lockMode = "TIME",
            deadlineReached = true
        )

        fakeFirestoreAdapter.onUnlockCallback = {
            sessionTracker.clearSession()
            sessionTracker.startSession("recipient-uid-1")
        }

        val result = repository.verifyUnlock("capsule-same-uid-relogin")
        assertTrue(result is OperationResult.Denied)
        assertTrue((result as OperationResult.Denied).message.contains("session"))
    }

    @Test
    fun `verifyUnlock fails closed on network unavailability`() = runBlocking {
        sessionTracker.startSession("recipient-uid-1")
        fakeFirestoreAdapter.simulateNetworkError = true

        val result = repository.verifyUnlock("capsule-105")

        assertTrue(result is OperationResult.Unavailable)
    }

    private class FakeAuthAdapterForUnlock(private val tracker: DefaultSessionTracker) : AuthAdapter {
        override fun activeUid(): String? = tracker.currentSession().uid
        override fun activeEmail(): String? = "test@example.com"
        override fun activeDisplayName(): String? = "Recipient User"
        override suspend fun signIn(email: String, password: String) = OperationResult.Success("recipient-uid-1")
        override suspend fun signUp(email: String, password: String, displayName: String) = OperationResult.Success("recipient-uid-1")
        override suspend fun updateDisplayName(displayName: String) = OperationResult.Success(Unit)
        override fun signOut() = tracker.clearSession()
    }

    private class FakeLocationAdapterForUnlock : LocationAdapter {
        var locationAvailable = true
        var fineLocationGranted = true
        var onGetLocationCallback: (() -> Unit)? = null
        var sampleResult = LocationSampleResult(
            status = LocationSampleStatus.SUCCESS,
            sample = ForegroundLocationSample(
                latitude = 37.5665,
                longitude = 126.9780,
                elapsedRealtimeNanos = 90_000_000_000L,
                horizontalAccuracyMeters = 15.0f
            )
        )

        override fun isLocationAvailable(): Boolean = locationAvailable
        override fun isFineLocationGranted(): Boolean = fineLocationGranted
        override suspend fun getCurrentLocation(): LocationSampleResult {
            onGetLocationCallback?.invoke()
            return sampleResult
        }
    }

    private class FakeFirestoreAdapterForUnlock : FirestoreAdapter {
        var simulateNetworkError = false
        var simulateLostResponseError = false
        var simulateLostResponseCommitted = false
        var onUnlockCallback: (() -> Unit)? = null
        var onTransactionRetryCallback: (() -> Unit)? = null
        private val capsules = mutableMapOf<String, TestCapsuleRecord>()

        data class TestCapsuleRecord(
            val capsuleId: String,
            val recipientUid: String,
            var unlocked: Boolean,
            val lockMode: String,
            val deadlineReached: Boolean,
            val unlockAt: Instant? = null,
            val locationTarget: LocationTarget? = null,
            val hasPendingWrites: Boolean = false,
            val isMalformedLocationTarget: Boolean = false
        )

        fun seedCapsule(
            capsuleId: String,
            recipientUid: String,
            unlocked: Boolean,
            lockMode: String,
            deadlineReached: Boolean,
            unlockAt: Instant? = null,
            locationTarget: LocationTarget? = null,
            hasPendingWrites: Boolean = false,
            isMalformedLocationTarget: Boolean = false
        ) {
            capsules[capsuleId] = TestCapsuleRecord(
                capsuleId, recipientUid, unlocked, lockMode, deadlineReached, unlockAt, locationTarget, hasPendingWrites, isMalformedLocationTarget
            )
        }

        fun isCapsuleUnlocked(capsuleId: String): Boolean =
            capsules[capsuleId]?.unlocked ?: false

        override fun isAvailable(): Boolean = true
        override fun firestoreInstance(): FirebaseFirestore? = null

        override suspend fun getRecipientProfile(uid: String) =
            OperationResult.Success(RecipientProfile(uid, "User $uid"))

        override suspend fun saveRecipientProfile(profile: RecipientProfile) =
            OperationResult.Success(Unit)

        override suspend fun readRecipientUnlockRequirements(
            capsuleId: String,
            recipientUid: String
        ): OperationResult<RecipientUnlockRequirements> {
            if (simulateNetworkError) {
                return OperationResult.Unavailable("Network unavailable.")
            }
            val record = capsules[capsuleId]
                ?: return OperationResult.Invalid("Capsule not found with ID: $capsuleId")

            if (record.recipientUid != recipientUid) {
                return OperationResult.Denied("Only the designated recipient can unlock this capsule.")
            }

            // P0: Reject entire unconfirmed/cached metadata snapshot!
            if (record.hasPendingWrites) {
                return OperationResult.Unavailable("Capsule metadata is unconfirmed or cached.")
            }

            val mode = when (record.lockMode) {
                "TIME" -> LockMode.TIME
                "LOCATION" -> LockMode.LOCATION
                "BOTH", "TIME_AND_LOCATION" -> LockMode.TIME_AND_LOCATION
                else -> return OperationResult.Invalid("Unsupported lock mode: ${record.lockMode}")
            }

            if (record.isMalformedLocationTarget) {
                if (mode == LockMode.LOCATION || mode == LockMode.TIME_AND_LOCATION) {
                    return OperationResult.Invalid("Capsule location target is malformed or invalid.")
                }
            }

            return OperationResult.Success(
                RecipientUnlockRequirements(
                    capsuleId = capsuleId,
                    recipientUid = recipientUid,
                    unlocked = record.unlocked,
                    lockMode = mode,
                    unlockAt = record.unlockAt,
                    locationTarget = record.locationTarget
                )
            )
        }

        override suspend fun verifyRecipientUnlock(
            capsuleId: String,
            recipientUid: String,
            locationSample: ForegroundLocationSample?,
            clockNanosSupplier: () -> Long
        ): OperationResult<Availability> {
            onUnlockCallback?.invoke()
            onTransactionRetryCallback?.invoke()

            if (simulateLostResponseCommitted) {
                val rec = capsules[capsuleId]
                if (rec != null) {
                    rec.unlocked = true
                    // Simulates DefaultFirestoreAdapter catch block reconciling committed server state
                    return OperationResult.Success(Availability.AVAILABLE)
                }
            }

            if (simulateNetworkError || simulateLostResponseError) {
                return OperationResult.Unavailable("Network response lost during unlock verification.")
            }
            val record = capsules[capsuleId]
                ?: return OperationResult.Invalid("Capsule not found with ID: $capsuleId")

            if (record.recipientUid != recipientUid) {
                return OperationResult.Denied("Only the designated recipient can unlock this capsule.")
            }

            if (record.unlocked && !record.hasPendingWrites) {
                return OperationResult.Success(Availability.AVAILABLE)
            }

            val mode = when (record.lockMode) {
                "TIME" -> LockMode.TIME
                "LOCATION" -> LockMode.LOCATION
                "BOTH", "TIME_AND_LOCATION" -> LockMode.TIME_AND_LOCATION
                else -> return OperationResult.Invalid("Unsupported lock mode: ${record.lockMode}")
            }

            if (mode == LockMode.LOCATION || mode == LockMode.TIME_AND_LOCATION) {
                if (locationSample == null) {
                    return OperationResult.Denied("Location sample is missing.")
                }
                val target = record.locationTarget
                    ?: return OperationResult.Invalid("Capsule location target is missing or malformed.")
                val nowNanos = clockNanosSupplier()
                val qual = LocationQualifier.qualify(target, locationSample, nowNanos)
                if (!qual.isQualifying) {
                    return when (qual.reason) {
                        QualificationFailureReason.OUTSIDE_RADIUS -> OperationResult.Denied("Current location is outside target radius.")
                        QualificationFailureReason.STALE_SAMPLE -> OperationResult.Unavailable("Location sample is stale. Please acquire a fresh location.")
                        QualificationFailureReason.POOR_ACCURACY -> OperationResult.Unavailable("Location sample accuracy is too poor (must be <= 100m).")
                        QualificationFailureReason.FUTURE_TIMESTAMP -> OperationResult.Unavailable("Location sample timestamp is invalid.")
                        QualificationFailureReason.INVALID_COORDINATES -> OperationResult.Invalid("Location sample contains invalid coordinates.")
                        QualificationFailureReason.INVALID_TARGET -> OperationResult.Invalid("Target location is invalid or does not have 150m radius.")
                        QualificationFailureReason.MISSING_SAMPLE -> OperationResult.Denied("Location sample is missing.")
                        QualificationFailureReason.NONE -> OperationResult.Denied("Location qualification failed.")
                    }
                }
            }

            if (mode == LockMode.TIME || mode == LockMode.TIME_AND_LOCATION) {
                if (!record.deadlineReached) {
                    return OperationResult.Denied("Unlock authorization denied or unlock time not reached.")
                }
            }

            record.unlocked = true
            return OperationResult.Success(Availability.AVAILABLE)
        }
    }
}
