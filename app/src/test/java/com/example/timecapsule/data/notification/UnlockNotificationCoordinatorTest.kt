package com.example.timecapsule.data.notification

import com.example.timecapsule.data.adapter.FirestoreAdapter
import com.example.timecapsule.data.session.DefaultSessionTracker
import com.example.timecapsule.domain.model.Availability
import com.example.timecapsule.domain.model.ForegroundLocationSample
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.model.RecipientUnlockRequirements
import com.example.timecapsule.domain.model.UnlockConfirmationKind
import com.example.timecapsule.domain.model.UnlockVerificationOutcome
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeNotificationAdapter : NotificationAdapter {
    var permitted: Boolean = true
    var channelCreated: Boolean = false
    var postResultToReturn: NotificationPostResult? = null
    val postedNotifications = mutableListOf<Pair<String, String>>()
    val cancelledAccounts = mutableListOf<String>()
    var throwOnNotify: Boolean = false

    override fun areNotificationsPermitted(): Boolean = permitted

    override fun createNotificationChannel() {
        channelCreated = true
    }

    override fun postUnlockNotification(accountUid: String, capsuleId: String): NotificationPostResult {
        if (throwOnNotify) throw RuntimeException("Notification service failure")
        postResultToReturn?.let { return it }
        if (!permitted) return NotificationPostResult.NOT_ATTEMPTED_PERMISSION_DENIED
        postedNotifications.add(accountUid to capsuleId)
        return NotificationPostResult.ATTEMPTED_SUCCESS
    }

    override fun cancelAccountNotifications(accountUid: String) {
        cancelledAccounts.add(accountUid)
    }
}

private class LegacyFirestoreAdapter : FirestoreAdapter {
    override fun isAvailable(): Boolean = true
    override fun firestoreInstance() = null
    override suspend fun getRecipientProfile(uid: String) = OperationResult.Unavailable("Not implemented")
    override suspend fun saveRecipientProfile(profile: RecipientProfile) = OperationResult.Unavailable("Not implemented")
    override suspend fun verifyRecipientUnlock(
        capsuleId: String,
        recipientUid: String,
        locationSample: ForegroundLocationSample?,
        clockNanosSupplier: () -> Long
    ): OperationResult<Availability> = OperationResult.Success(Availability.AVAILABLE)
}

/**
 * Unit tests for [UnlockNotificationCoordinator] verifying decision table behavior across
 * permission states, deduplication, session drift, storage failures, and error isolation.
 */
class UnlockNotificationCoordinatorTest {

    private lateinit var tempFile: File
    private lateinit var attemptStore: NotificationAttemptStore
    private lateinit var fakeAdapter: FakeNotificationAdapter
    private lateinit var sessionTracker: DefaultSessionTracker
    private lateinit var coordinator: UnlockNotificationCoordinator

    @Before
    fun setUp() {
        tempFile = File.createTempFile("notification_coord_test", ".json")
        attemptStore = NotificationAttemptStore(tempFile)
        fakeAdapter = FakeNotificationAdapter()
        sessionTracker = DefaultSessionTracker()
        coordinator = UnlockNotificationCoordinator(fakeAdapter, attemptStore, sessionTracker)
    }

    @After
    fun tearDown() {
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }

    @Test
    fun `confirmed first unlock with permission allowed posts notification and claims state`() {
        sessionTracker.startSession("recipient-uid-1")
        val session = sessionTracker.currentSession()
        val outcome = UnlockVerificationOutcome(Availability.AVAILABLE, UnlockConfirmationKind.FIRST_UNLOCK_CONFIRMED)

        coordinator.handleUnlockOutcome("capsule-101", outcome, session)

        assertEquals(1, fakeAdapter.postedNotifications.size)
        assertEquals("recipient-uid-1" to "capsule-101", fakeAdapter.postedNotifications.first())
        assertEquals(AttemptState.ATTEMPTED, attemptStore.getAttemptState("recipient-uid-1", "capsule-101"))
    }

    @Test
    fun `confirmed first unlock with permission denied records PENDING_PERMISSION without posting`() {
        sessionTracker.startSession("recipient-uid-1")
        val session = sessionTracker.currentSession()
        fakeAdapter.permitted = false
        val outcome = UnlockVerificationOutcome(Availability.AVAILABLE, UnlockConfirmationKind.FIRST_UNLOCK_CONFIRMED)

        coordinator.handleUnlockOutcome("capsule-101", outcome, session)

        assertTrue(fakeAdapter.postedNotifications.isEmpty())
        assertEquals(AttemptState.PENDING_PERMISSION, attemptStore.getAttemptState("recipient-uid-1", "capsule-101"))
    }

    @Test
    fun `already unlocked capsule with PENDING_PERMISSION candidate posts once when permission granted`() {
        sessionTracker.startSession("recipient-uid-1")
        val session = sessionTracker.currentSession()
        attemptStore.recordPendingPermission("recipient-uid-1", "capsule-101")

        fakeAdapter.permitted = true
        val outcome = UnlockVerificationOutcome(Availability.AVAILABLE, UnlockConfirmationKind.ALREADY_UNLOCKED)

        coordinator.handleUnlockOutcome("capsule-101", outcome, session)

        assertEquals(1, fakeAdapter.postedNotifications.size)
        assertEquals(AttemptState.ATTEMPTED, attemptStore.getAttemptState("recipient-uid-1", "capsule-101"))
    }

    @Test
    fun `already unlocked capsule with ABSENT state does not create historical notification`() {
        sessionTracker.startSession("recipient-uid-1")
        val session = sessionTracker.currentSession()
        val outcome = UnlockVerificationOutcome(Availability.AVAILABLE, UnlockConfirmationKind.ALREADY_UNLOCKED)

        coordinator.handleUnlockOutcome("capsule-101", outcome, session)

        assertTrue(fakeAdapter.postedNotifications.isEmpty())
        assertEquals(AttemptState.ABSENT, attemptStore.getAttemptState("recipient-uid-1", "capsule-101"))
    }

    @Test
    fun `repeated check on ATTEMPTED state does not post again`() {
        sessionTracker.startSession("recipient-uid-1")
        val session = sessionTracker.currentSession()
        val outcome = UnlockVerificationOutcome(Availability.AVAILABLE, UnlockConfirmationKind.FIRST_UNLOCK_CONFIRMED)

        coordinator.handleUnlockOutcome("capsule-101", outcome, session)
        coordinator.handleUnlockOutcome("capsule-101", outcome, session)

        assertEquals(1, fakeAdapter.postedNotifications.size)
    }

    @Test
    fun `unwritable attempt store prevents notification posting`() {
        val unwritableFile = File(tempFile.parentFile, "unwritable_dir/store.json")
        val parentAsFile = File(tempFile.parentFile, "unwritable_dir")
        parentAsFile.createNewFile()
        parentAsFile.setWritable(false)

        try {
            val unwritableStore = NotificationAttemptStore(unwritableFile)
            val unwritableCoordinator = UnlockNotificationCoordinator(fakeAdapter, unwritableStore, sessionTracker)

            sessionTracker.startSession("recipient-uid-1")
            val session = sessionTracker.currentSession()
            val outcome = UnlockVerificationOutcome(Availability.AVAILABLE, UnlockConfirmationKind.FIRST_UNLOCK_CONFIRMED)

            unwritableCoordinator.handleUnlockOutcome("capsule-101", outcome, session)

            assertTrue("Notification adapter must NOT be called when storage claim fails", fakeAdapter.postedNotifications.isEmpty())
        } finally {
            parentAsFile.setWritable(true)
            parentAsFile.delete()
        }
    }

    @Test
    fun `permission revoked at posting time restores pending permission state`() {
        sessionTracker.startSession("recipient-uid-1")
        val session = sessionTracker.currentSession()

        fakeAdapter.permitted = true
        fakeAdapter.postResultToReturn = NotificationPostResult.NOT_ATTEMPTED_PERMISSION_DENIED

        val outcome = UnlockVerificationOutcome(Availability.AVAILABLE, UnlockConfirmationKind.FIRST_UNLOCK_CONFIRMED)
        coordinator.handleUnlockOutcome("capsule-101", outcome, session)

        assertEquals(AttemptState.PENDING_PERMISSION, attemptStore.getAttemptState("recipient-uid-1", "capsule-101"))

        // Later when permission is restored and candidate rechecked
        fakeAdapter.postResultToReturn = null
        fakeAdapter.permitted = true
        val recheckOutcome = UnlockVerificationOutcome(Availability.AVAILABLE, UnlockConfirmationKind.ALREADY_UNLOCKED)
        coordinator.handleUnlockOutcome("capsule-101", recheckOutcome, session)

        assertEquals(1, fakeAdapter.postedNotifications.size)
        assertEquals(AttemptState.ATTEMPTED, attemptStore.getAttemptState("recipient-uid-1", "capsule-101"))
    }

    @Test
    fun `legacy adapter default fallback yields ALREADY_UNLOCKED not FIRST_UNLOCK_CONFIRMED`() = runBlocking {
        val legacyAdapter = LegacyFirestoreAdapter()
        val result = legacyAdapter.verifyRecipientUnlockDetailed("capsule-101", "recipient-uid-1")

        assertTrue(result is OperationResult.Success)
        val outcome = (result as OperationResult.Success).data
        assertEquals(Availability.AVAILABLE, outcome.availability)
        assertEquals(
            "Legacy fallback must return ALREADY_UNLOCKED to prevent false notification alerts",
            UnlockConfirmationKind.ALREADY_UNLOCKED,
            outcome.confirmationKind
        )
    }

    @Test
    fun `stale session snapshot discards notification attempt without state change`() {
        sessionTracker.startSession("recipient-uid-1")
        val session1 = sessionTracker.currentSession()
        sessionTracker.startSession("recipient-uid-1") // Session generation 2

        val outcome = UnlockVerificationOutcome(Availability.AVAILABLE, UnlockConfirmationKind.FIRST_UNLOCK_CONFIRMED)
        coordinator.handleUnlockOutcome("capsule-101", outcome, session1)

        assertTrue(fakeAdapter.postedNotifications.isEmpty())
        assertEquals(AttemptState.ABSENT, attemptStore.getAttemptState("recipient-uid-1", "capsule-101"))
    }

    @Test
    fun `invalidateAccount cancels account notifications and clears attempt store`() {
        sessionTracker.startSession("recipient-uid-1")
        attemptStore.recordPendingPermission("recipient-uid-1", "capsule-101")

        coordinator.invalidateAccount("recipient-uid-1")

        assertEquals(1, fakeAdapter.cancelledAccounts.size)
        assertEquals("recipient-uid-1", fakeAdapter.cancelledAccounts.first())
        assertEquals(AttemptState.ABSENT, attemptStore.getAttemptState("recipient-uid-1", "capsule-101"))
    }

    @Test
    fun `notification adapter exception is isolated without throwing`() {
        sessionTracker.startSession("recipient-uid-1")
        val session = sessionTracker.currentSession()
        fakeAdapter.throwOnNotify = true
        val outcome = UnlockVerificationOutcome(Availability.AVAILABLE, UnlockConfirmationKind.FIRST_UNLOCK_CONFIRMED)

        // Should not throw exception
        coordinator.handleUnlockOutcome("capsule-101", outcome, session)
    }
}
