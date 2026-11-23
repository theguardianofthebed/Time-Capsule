package com.example.timecapsule.data

import com.example.timecapsule.data.adapter.AuthAdapter
import com.example.timecapsule.data.adapter.FirestoreAdapter
import com.example.timecapsule.data.session.DefaultSessionTracker
import com.example.timecapsule.domain.model.Availability
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.result.OperationResult
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM Unit tests verifying [DefaultCapsuleRepository.verifyUnlock] behavior under
 * authenticated recipient checks, server-authorized deadlines, device clock manipulation
 * safety, idempotent unlock status, and non-recipient denials.
 */
class DefaultCapsuleRepositoryUnlockTest {

    private lateinit var sessionTracker: DefaultSessionTracker
    private lateinit var fakeAuthAdapter: FakeAuthAdapterForUnlock
    private lateinit var fakeFirestoreAdapter: FakeFirestoreAdapterForUnlock
    private lateinit var repository: DefaultCapsuleRepository

    @Before
    fun setUp() {
        sessionTracker = DefaultSessionTracker()
        fakeAuthAdapter = FakeAuthAdapterForUnlock(sessionTracker)
        fakeFirestoreAdapter = FakeFirestoreAdapterForUnlock()
        repository = DefaultCapsuleRepository(
            sessionTracker = sessionTracker,
            authAdapter = fakeAuthAdapter,
            firestoreAdapter = fakeFirestoreAdapter
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
    fun `verifyUnlock denies early unlock before server deadline`() = runBlocking {
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
    fun `verifyUnlock succeeds for recipient at or after server deadline`() = runBlocking {
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
    fun `verifyUnlock treats already unlocked capsule as idempotent success`() = runBlocking {
        sessionTracker.startSession("recipient-uid-1")
        fakeFirestoreAdapter.seedCapsule(
            capsuleId = "capsule-104",
            recipientUid = "recipient-uid-1",
            unlocked = true,
            lockMode = "TIME",
            deadlineReached = true
        )

        // First unlock call succeeds
        val result1 = repository.verifyUnlock("capsule-104")
        assertTrue(result1 is OperationResult.Success)

        // Second unlock call also succeeds (idempotent)
        val result2 = repository.verifyUnlock("capsule-104")
        assertTrue(result2 is OperationResult.Success)
        assertEquals(Availability.AVAILABLE, (result2 as OperationResult.Success).data)
    }

    @Test
    fun `verifyUnlock fails closed on network unavailability`() = runBlocking {
        sessionTracker.startSession("recipient-uid-1")
        fakeFirestoreAdapter.simulateNetworkError = true

        val result = repository.verifyUnlock("capsule-105")

        assertTrue(result is OperationResult.Unavailable)
    }

    @Test
    fun `verifyUnlock denies location and combined modes with Issue 17 notice`() = runBlocking {
        sessionTracker.startSession("recipient-uid-1")
        fakeFirestoreAdapter.seedCapsule(
            capsuleId = "capsule-loc",
            recipientUid = "recipient-uid-1",
            unlocked = false,
            lockMode = "LOCATION",
            deadlineReached = true
        )

        val resultLoc = repository.verifyUnlock("capsule-loc")
        assertTrue(resultLoc is OperationResult.Denied)
        assertTrue((resultLoc as OperationResult.Denied).message.contains("Issue #17"))

        fakeFirestoreAdapter.seedCapsule(
            capsuleId = "capsule-both",
            recipientUid = "recipient-uid-1",
            unlocked = false,
            lockMode = "TIME_AND_LOCATION",
            deadlineReached = true
        )

        val resultBoth = repository.verifyUnlock("capsule-both")
        assertTrue(resultBoth is OperationResult.Denied)
        assertTrue((resultBoth as OperationResult.Denied).message.contains("Issue #17"))
    }

    @Test
    fun `verifyUnlock denies BOTH lock mode with Issue 17 notice`() = runBlocking {
        sessionTracker.startSession("recipient-uid-1")
        fakeFirestoreAdapter.seedCapsule(
            capsuleId = "capsule-canonical-both",
            recipientUid = "recipient-uid-1",
            unlocked = false,
            lockMode = "BOTH",
            deadlineReached = true
        )

        val resultBoth = repository.verifyUnlock("capsule-canonical-both")
        assertTrue(resultBoth is OperationResult.Denied)
        assertTrue((resultBoth as OperationResult.Denied).message.contains("Issue #17"))
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

    private class FakeAuthAdapterForUnlock(private val tracker: DefaultSessionTracker) : AuthAdapter {
        override fun activeUid(): String? = tracker.currentSession().uid
        override fun activeEmail(): String? = "test@example.com"
        override fun activeDisplayName(): String? = "Recipient User"
        override suspend fun signIn(email: String, password: String) = OperationResult.Success("recipient-uid-1")
        override suspend fun signUp(email: String, password: String, displayName: String) = OperationResult.Success("recipient-uid-1")
        override suspend fun updateDisplayName(displayName: String) = OperationResult.Success(Unit)
        override fun signOut() = tracker.clearSession()
    }

    private class FakeFirestoreAdapterForUnlock : FirestoreAdapter {
        var simulateNetworkError = false
        var onUnlockCallback: (() -> Unit)? = null
        private val capsules = mutableMapOf<String, TestCapsuleRecord>()

        data class TestCapsuleRecord(
            val capsuleId: String,
            val recipientUid: String,
            var unlocked: Boolean,
            val lockMode: String,
            val deadlineReached: Boolean
        )

        fun seedCapsule(
            capsuleId: String,
            recipientUid: String,
            unlocked: Boolean,
            lockMode: String,
            deadlineReached: Boolean
        ) {
            capsules[capsuleId] = TestCapsuleRecord(
                capsuleId, recipientUid, unlocked, lockMode, deadlineReached
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

        override suspend fun verifyRecipientUnlock(
            capsuleId: String,
            recipientUid: String
        ): OperationResult<Availability> {
            onUnlockCallback?.invoke()
            if (simulateNetworkError) {
                return OperationResult.Unavailable("Network unavailable during unlock verification.")
            }
            val record = capsules[capsuleId]
                ?: return OperationResult.Invalid("Capsule not found with ID: $capsuleId")

            if (record.recipientUid != recipientUid) {
                return OperationResult.Denied("Only the designated recipient can unlock this capsule.")
            }

            if (record.unlocked) {
                return OperationResult.Success(Availability.AVAILABLE)
            }

            return when (record.lockMode) {
                "TIME" -> {
                    if (!record.deadlineReached) {
                        OperationResult.Denied("Unlock authorization denied or unlock time not reached.")
                    } else {
                        record.unlocked = true
                        OperationResult.Success(Availability.AVAILABLE)
                    }
                }
                "LOCATION" -> {
                    OperationResult.Denied("Location unlock condition requires Issue #17 implementation.")
                }
                "TIME_AND_LOCATION", "BOTH" -> {
                    OperationResult.Denied("Combined time and location unlock condition requires Issue #17 implementation.")
                }
                else -> OperationResult.Invalid("Unsupported lock mode")
            }
        }
    }
}
