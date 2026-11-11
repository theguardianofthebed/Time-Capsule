package com.example.timecapsule.data

import com.example.timecapsule.data.session.DefaultSessionTracker
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.result.OperationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipientConfirmationTest {

    @Test
    fun recipientLookupTrimsWhitespaceAndReturnsConfirmationState() = runBlocking {
        val authAdapter = FakeAuthAdapterForRepair()
        val firestoreAdapter = FakeFirestoreAdapterForRepair().apply {
            savedProfiles["user-target-456"] = RecipientProfile("user-target-456", "Target User")
        }
        val sessionTracker = DefaultSessionTracker()
        val manager = AccountSessionManager(authAdapter, firestoreAdapter, sessionTracker)

        val res = manager.lookupRecipient("  user-target-456  ")
        assertTrue(res is OperationResult.Success)

        val confirmation = (res as OperationResult.Success).data
        assertEquals("user-target-456", confirmation.profile.uid)
        assertEquals("Target User", confirmation.profile.displayName)
        assertFalse("Newly looked up recipient must not be confirmed yet", confirmation.isConfirmed)

        val confirmedState = manager.confirmRecipient(confirmation, true)
        assertTrue("Recipient must be marked as explicitly confirmed", confirmedState.isConfirmed)
    }

    @Test
    fun changingTargetUidInvalidatesConfirmationState() = runBlocking {
        val authAdapter = FakeAuthAdapterForRepair()
        val firestoreAdapter = FakeFirestoreAdapterForRepair().apply {
            savedProfiles["user-target-456"] = RecipientProfile("user-target-456", "Target User")
            savedProfiles["user-target-789"] = RecipientProfile("user-target-789", "New Target User")
        }
        val sessionTracker = DefaultSessionTracker()
        val manager = AccountSessionManager(authAdapter, firestoreAdapter, sessionTracker)

        val res1 = manager.lookupRecipient("user-target-456")
        val confirmed1 = manager.confirmRecipient((res1 as OperationResult.Success).data, true)
        assertTrue(confirmed1.isConfirmed)

        // Lookup changes to new UID -> returns new RecipientConfirmation with isConfirmed = false
        val res2 = manager.lookupRecipient("user-target-789")
        assertTrue(res2 is OperationResult.Success)
        assertFalse("New recipient lookup must reset confirmation status to false", (res2 as OperationResult.Success).data.isConfirmed)
    }
}
