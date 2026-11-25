package com.example.timecapsule.data.notification

import com.example.timecapsule.data.CapsuleRepository
import com.example.timecapsule.data.session.DefaultSessionTracker
import com.example.timecapsule.domain.model.Availability
import com.example.timecapsule.domain.model.CapsuleDraft
import com.example.timecapsule.domain.model.ProtectedCapsuleContent
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.model.UnlockConfirmationKind
import com.example.timecapsule.domain.model.UnlockVerificationOutcome
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeEntryRepository : CapsuleRepository {
    var outcomeToReturn: OperationResult<UnlockVerificationOutcome> = OperationResult.Success(
        UnlockVerificationOutcome(Availability.AVAILABLE, UnlockConfirmationKind.FIRST_UNLOCK_CONFIRMED)
    )

    override fun session(): SessionSnapshot = SessionSnapshot(uid = "recipient-1", sessionGeneration = 1)
    override suspend fun getRecipient(uid: String) = OperationResult.Unavailable("Not implemented")
    override suspend fun publish(frozenOperation: CapsuleDraft) = OperationResult.Unavailable("Not implemented")
    override suspend fun reconcile(capsuleId: String) = OperationResult.Unavailable("Not implemented")
    override suspend fun lists(uid: String) = OperationResult.Unavailable("Not implemented")
    override suspend fun verifyUnlock(capsuleId: String) = OperationResult.Success(Availability.AVAILABLE)
    override suspend fun verifyUnlockOutcome(capsuleId: String, targetSession: SessionSnapshot): OperationResult<UnlockVerificationOutcome> = outcomeToReturn
    override suspend fun readContent(capsuleId: String) = OperationResult.Unavailable("Not implemented")
}

/**
 * Unit tests for [NotificationEntryValidator] verifying intent payload validation,
 * account matching, and authoritative repository unlock authorization.
 */
class NotificationEntryValidatorTest {

    private lateinit var fakeRepository: FakeEntryRepository
    private lateinit var sessionTracker: DefaultSessionTracker
    private lateinit var validator: NotificationEntryValidator

    @Before
    fun setUp() {
        fakeRepository = FakeEntryRepository()
        sessionTracker = DefaultSessionTracker()
        sessionTracker.startSession("recipient-1")
        validator = NotificationEntryValidator(fakeRepository, sessionTracker)
    }

    @Test
    fun `null or invalid action payload returns Invalid`() = runBlocking {
        val result = validator.validateEntryPayload(
            action = "WRONG_ACTION",
            accountUid = "recipient-1",
            capsuleId = "capsule-101"
        )
        assertTrue(result is NotificationEntryResult.Invalid)
    }

    @Test
    fun `payload missing account UID or capsule ID returns Invalid`() = runBlocking {
        val result = validator.validateEntryPayload(
            action = DefaultNotificationAdapter.ACTION_NOTIFICATION_TAP,
            accountUid = " ",
            capsuleId = "capsule-101"
        )
        assertTrue(result is NotificationEntryResult.Invalid)
    }

    @Test
    fun `payload with mismatched account UID returns Invalid`() = runBlocking {
        val result = validator.validateEntryPayload(
            action = DefaultNotificationAdapter.ACTION_NOTIFICATION_TAP,
            accountUid = "other-account",
            capsuleId = "capsule-101"
        )
        assertTrue(result is NotificationEntryResult.Invalid)
    }

    @Test
    fun `payload matching active session and unlocked capsule returns Valid`() = runBlocking {
        val result = validator.validateEntryPayload(
            action = DefaultNotificationAdapter.ACTION_NOTIFICATION_TAP,
            accountUid = "recipient-1",
            capsuleId = "capsule-101"
        )
        assertTrue(result is NotificationEntryResult.Valid)
        assertEquals("capsule-101", (result as NotificationEntryResult.Valid).capsuleId)
    }

    @Test
    fun `payload targeting locked capsule returns Invalid`() = runBlocking {
        fakeRepository.outcomeToReturn = OperationResult.Denied("Capsule remains locked.")
        val result = validator.validateEntryPayload(
            action = DefaultNotificationAdapter.ACTION_NOTIFICATION_TAP,
            accountUid = "recipient-1",
            capsuleId = "capsule-101"
        )
        assertTrue(result is NotificationEntryResult.Invalid)
    }
}
