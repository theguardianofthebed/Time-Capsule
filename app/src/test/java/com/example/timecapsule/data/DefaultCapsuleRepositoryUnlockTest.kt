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
 * missing permissions/services, session race protection, and idempotent unlocked state.
 */
class DefaultCapsuleRepositoryUnlockTest {

    private lateinit var sessionTracker: DefaultSessionTracker
    private lateinit var fakeAuthAdapter: FakeAuthAdapterForUnlock
    private lateinit var fakeFirestoreAdapter: FakeFirestoreAdapterForUnlock
    private lateinit var fakeLocationAdapter: FakeLocationAdapterForUnlock
    private lateinit var repository: DefaultCapsuleRepository

    private val targetSeoul = LocationTarget(latitude = 37.5665, longitude = 126.9780, radiusMeters = 150.0)
    private val testNowNanos = 100_000_000_000L

    @Before
    fun setUp() {
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
    fun `verifyUnlock treats already unlocked capsule as idempotent success without querying GPS`() = runBlocking {
        sessionTracker.startSession("recipient-uid-1")
        fakeFirestoreAdapter.seedCapsule(
            capsuleId = "capsule-104",
            recipientUid = "recipient-uid-1",
            unlocked = true,
            lockMode = "LOCATION",
            deadlineReached = true,
            locationTarget = targetSeoul
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
    fun `verifyUnlock denies LOCATION mode when permission is missing`() = runBlocking {
        sessionTracker.startSession("recipient-uid-1")
        fakeFirestoreAdapter.seedCapsule(
            capsuleId = "capsule-loc-perm",
            recipientUid = "recipient-uid-1",
            unlocked = false,
            lockMode = "LOCATION",
            deadlineReached = true,
            locationTarget = targetSeoul
        )
        fakeLocationAdapter.fineLocationGranted = false

        val result = repository.verifyUnlock("capsule-loc-perm")

        assertTrue(result is OperationResult.Denied)
        val message = (result as OperationResult.Denied).message
        assertTrue(message.contains("permission"))
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

        // Attempt 2: Fresh foreground fix succeeds without needing exit/re-entry
        val attempt2 = repository.verifyUnlock("t20-capsule")
        assertTrue(attempt2 is OperationResult.Success)
        assertEquals(Availability.AVAILABLE, (attempt2 as OperationResult.Success).data)
        assertTrue(fakeFirestoreAdapter.isCapsuleUnlocked("t20-capsule"))
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
        var fineLocationGranted = true
        var sampleResult = LocationSampleResult(
            status = LocationSampleStatus.SUCCESS,
            sample = ForegroundLocationSample(
                latitude = 37.5665,
                longitude = 126.9780,
                elapsedRealtimeNanos = 90_000_000_000L,
                horizontalAccuracyMeters = 15.0f
            )
        )

        override fun isLocationAvailable(): Boolean = true
        override fun isFineLocationGranted(): Boolean = fineLocationGranted
        override suspend fun getCurrentLocation(): LocationSampleResult = sampleResult
    }

    private class FakeFirestoreAdapterForUnlock : FirestoreAdapter {
        var simulateNetworkError = false
        var simulateLostResponseError = false
        var onUnlockCallback: (() -> Unit)? = null
        private val capsules = mutableMapOf<String, TestCapsuleRecord>()

        data class TestCapsuleRecord(
            val capsuleId: String,
            val recipientUid: String,
            var unlocked: Boolean,
            val lockMode: String,
            val deadlineReached: Boolean,
            val unlockAt: Instant? = null,
            val locationTarget: LocationTarget? = null
        )

        fun seedCapsule(
            capsuleId: String,
            recipientUid: String,
            unlocked: Boolean,
            lockMode: String,
            deadlineReached: Boolean,
            unlockAt: Instant? = null,
            locationTarget: LocationTarget? = null
        ) {
            capsules[capsuleId] = TestCapsuleRecord(
                capsuleId, recipientUid, unlocked, lockMode, deadlineReached, unlockAt, locationTarget
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

            val mode = when (record.lockMode) {
                "TIME" -> LockMode.TIME
                "LOCATION" -> LockMode.LOCATION
                "BOTH", "TIME_AND_LOCATION" -> LockMode.TIME_AND_LOCATION
                else -> return OperationResult.Invalid("Unsupported lock mode: ${record.lockMode}")
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
            if (simulateNetworkError || simulateLostResponseError) {
                return OperationResult.Unavailable("Network response lost during unlock verification.")
            }
            val record = capsules[capsuleId]
                ?: return OperationResult.Invalid("Capsule not found with ID: $capsuleId")

            if (record.recipientUid != recipientUid) {
                return OperationResult.Denied("Only the designated recipient can unlock this capsule.")
            }

            if (record.unlocked) {
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
                    ?: return OperationResult.Invalid("Capsule target location is missing.")
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
