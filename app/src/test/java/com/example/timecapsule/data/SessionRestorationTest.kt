package com.example.timecapsule.data

import com.example.timecapsule.data.adapter.AuthAdapter
import com.example.timecapsule.data.adapter.FirestoreAdapter
import com.example.timecapsule.data.session.DefaultSessionTracker
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.result.OperationResult
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeAuthAdapterWithActiveUid(private var uid: String?) : AuthAdapter {
    override fun activeUid(): String? = uid
    override fun activeEmail(): String? = uid?.let { "$it@example.com" }
    override fun activeDisplayName(): String? = uid?.let { "Name of $it" }
    override suspend fun signIn(email: String, password: String): OperationResult<String> {
        uid = "user-signed-in"
        return OperationResult.Success(uid!!)
    }
    override suspend fun signUp(email: String, password: String, displayName: String): OperationResult<String> {
        uid = "user-signed-up"
        return OperationResult.Success(uid!!)
    }
    override suspend fun updateDisplayName(displayName: String): OperationResult<Unit> = OperationResult.Success(Unit)
    override fun signOut() { uid = null }
}

class FakeFirestoreAdapterForSession : FirestoreAdapter {
    override fun isAvailable(): Boolean = true
    override fun firestoreInstance(): FirebaseFirestore? = null
    override suspend fun getRecipientProfile(uid: String): OperationResult<RecipientProfile> = OperationResult.Success(RecipientProfile(uid, "Name"))
    override suspend fun saveRecipientProfile(profile: RecipientProfile): OperationResult<Unit> = OperationResult.Success(Unit)
}

class SessionRestorationTest {

    /** Repeated foreground authentication checks preserve active work's generation evidence. */
    @Test fun repeatedRestorationDoesNotCreateNewSession() = runBlocking {
        val tracker = DefaultSessionTracker()
        val manager = AccountSessionManager(FakeAuthAdapterWithActiveUid("same"),
            FakeFirestoreAdapterForSession(), tracker)
        manager.restoreSessionIfAuthenticated()
        val before = tracker.currentSession()
        repeat(3) { manager.restoreSessionIfAuthenticated() }
        assertEquals(before, tracker.currentSession())
    }

    /** An already-signed-out foreground check does not invalidate the same empty session repeatedly. */
    @Test fun repeatedSignedOutChecksPreserveGeneration() = runBlocking {
        val tracker = DefaultSessionTracker()
        val manager = AccountSessionManager(FakeAuthAdapterWithActiveUid(null),
            FakeFirestoreAdapterForSession(), tracker)
        val before = tracker.currentSession()
        repeat(3) { manager.restoreSessionIfAuthenticated() }
        assertEquals(before, tracker.currentSession())
    }

    @Test
    fun restoredSessionPopulatesSessionTrackerAndActiveUid() = runBlocking {
        val authAdapter = FakeAuthAdapterWithActiveUid("user-restored-123")
        val sessionTracker = DefaultSessionTracker()
        val manager = AccountSessionManager(authAdapter, FakeFirestoreAdapterForSession(), sessionTracker)

        val restoredUid = manager.restoreSessionIfAuthenticated()

        assertEquals("user-restored-123", restoredUid)
        assertTrue(sessionTracker.currentSession().isAuthenticated)
        assertEquals("user-restored-123", sessionTracker.currentSession().uid)
    }

    @Test
    fun unauthenticatedRestartClearsSessionTracker() = runBlocking {
        val authAdapter = FakeAuthAdapterWithActiveUid(null)
        val sessionTracker = DefaultSessionTracker()
        val manager = AccountSessionManager(authAdapter, FakeFirestoreAdapterForSession(), sessionTracker)

        val restoredUid = manager.restoreSessionIfAuthenticated()

        assertNull(restoredUid)
        assertFalse(sessionTracker.currentSession().isAuthenticated)
    }

    @Test
    fun signOutClearsRestoredSession() = runBlocking {
        val authAdapter = FakeAuthAdapterWithActiveUid("user-restored-123")
        val sessionTracker = DefaultSessionTracker()
        val manager = AccountSessionManager(authAdapter, FakeFirestoreAdapterForSession(), sessionTracker)

        manager.restoreSessionIfAuthenticated()
        manager.signOut()

        assertNull(authAdapter.activeUid())
        assertFalse(sessionTracker.currentSession().isAuthenticated)
    }

    @Test
    fun repeatedRestorePreservesSessionGeneration() = runBlocking {
        val authAdapter = FakeAuthAdapterWithActiveUid("user-restored-123")
        val sessionTracker = DefaultSessionTracker()
        val manager = AccountSessionManager(authAdapter, FakeFirestoreAdapterForSession(), sessionTracker)

        val firstUid = manager.restoreSessionIfAuthenticated()
        val initialGen = sessionTracker.currentSession().sessionGeneration

        val secondUid = manager.restoreSessionIfAuthenticated()
        val secondGen = sessionTracker.currentSession().sessionGeneration

        assertEquals("user-restored-123", firstUid)
        assertEquals("user-restored-123", secondUid)
        assertEquals(initialGen, secondGen)
    }

    @Test
    fun repeatedUnauthenticatedRestorePreservesGeneration() = runBlocking {
        val authAdapter = FakeAuthAdapterWithActiveUid(null)
        val sessionTracker = DefaultSessionTracker()
        val manager = AccountSessionManager(authAdapter, FakeFirestoreAdapterForSession(), sessionTracker)

        manager.restoreSessionIfAuthenticated()
        val initialGen = sessionTracker.currentSession().sessionGeneration

        manager.restoreSessionIfAuthenticated()
        val secondGen = sessionTracker.currentSession().sessionGeneration

        assertEquals(initialGen, secondGen)
        assertFalse(sessionTracker.currentSession().isAuthenticated)
    }

    @Test
    fun accountTransitionIncrementsGenerationAndInvalidatesPreviousSession() = runBlocking {
        val authAdapter = FakeAuthAdapterWithActiveUid("user-1")
        val sessionTracker = DefaultSessionTracker()
        val manager = AccountSessionManager(authAdapter, FakeFirestoreAdapterForSession(), sessionTracker)

        manager.restoreSessionIfAuthenticated()
        val snapshot1 = sessionTracker.currentSession()

        // Account changes to user-2
        val newAdapter = FakeAuthAdapterWithActiveUid("user-2")
        val manager2 = AccountSessionManager(newAdapter, FakeFirestoreAdapterForSession(), sessionTracker)
        manager2.restoreSessionIfAuthenticated()
        val snapshot2 = sessionTracker.currentSession()

        assertEquals("user-1", snapshot1.uid)
        assertEquals("user-2", snapshot2.uid)
        assertTrue(snapshot2.sessionGeneration > snapshot1.sessionGeneration)
        assertFalse(sessionTracker.isSessionValid(snapshot1))
    }
}
