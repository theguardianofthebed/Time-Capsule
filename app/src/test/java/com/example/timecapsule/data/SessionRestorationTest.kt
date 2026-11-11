package com.example.timecapsule.data

import com.example.timecapsule.data.adapter.AuthAdapter
import com.example.timecapsule.data.adapter.FirestoreAdapter
import com.example.timecapsule.data.session.DefaultSessionTracker
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.result.OperationResult
import com.google.firebase.firestore.FirebaseFirestore
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

    @Test
    fun restoredSessionPopulatesSessionTrackerAndActiveUid() {
        val authAdapter = FakeAuthAdapterWithActiveUid("user-restored-123")
        val sessionTracker = DefaultSessionTracker()
        val manager = AccountSessionManager(authAdapter, FakeFirestoreAdapterForSession(), sessionTracker)

        val restoredUid = manager.restoreSessionIfAuthenticated()

        assertEquals("user-restored-123", restoredUid)
        assertTrue(sessionTracker.currentSession().isAuthenticated)
        assertEquals("user-restored-123", sessionTracker.currentSession().uid)
    }

    @Test
    fun unauthenticatedRestartClearsSessionTracker() {
        val authAdapter = FakeAuthAdapterWithActiveUid(null)
        val sessionTracker = DefaultSessionTracker()
        val manager = AccountSessionManager(authAdapter, FakeFirestoreAdapterForSession(), sessionTracker)

        val restoredUid = manager.restoreSessionIfAuthenticated()

        assertNull(restoredUid)
        assertFalse(sessionTracker.currentSession().isAuthenticated)
    }

    @Test
    fun signOutClearsRestoredSession() {
        val authAdapter = FakeAuthAdapterWithActiveUid("user-restored-123")
        val sessionTracker = DefaultSessionTracker()
        val manager = AccountSessionManager(authAdapter, FakeFirestoreAdapterForSession(), sessionTracker)

        manager.restoreSessionIfAuthenticated()
        manager.signOut()

        assertNull(authAdapter.activeUid())
        assertFalse(sessionTracker.currentSession().isAuthenticated)
    }
}
