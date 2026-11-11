package com.example.timecapsule.data

import com.example.timecapsule.data.adapter.AuthAdapter
import com.example.timecapsule.data.adapter.FirestoreAdapter
import com.example.timecapsule.data.session.DefaultSessionTracker
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.result.OperationResult
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeAuthAdapterForRepair : AuthAdapter {
    var uid: String? = null
    var name: String? = null

    override fun activeUid(): String? = uid
    override fun activeEmail(): String? = uid?.let { "$it@example.com" }
    override fun activeDisplayName(): String? = name

    override suspend fun signIn(email: String, password: String): OperationResult<String> {
        uid = "user-repair-123"
        return OperationResult.Success(uid!!)
    }

    override suspend fun signUp(email: String, password: String, displayName: String): OperationResult<String> {
        uid = "user-repair-123"
        name = displayName
        return OperationResult.Success(uid!!)
    }

    override suspend fun updateDisplayName(displayName: String): OperationResult<Unit> {
        name = displayName
        return OperationResult.Success(Unit)
    }

    override fun signOut() {
        uid = null
        name = null
    }
}

class FakeFirestoreAdapterForRepair : FirestoreAdapter {
    var shouldFailSave: Boolean = false
    val savedProfiles = mutableMapOf<String, RecipientProfile>()

    override fun isAvailable(): Boolean = true
    override fun firestoreInstance(): FirebaseFirestore? = null

    override suspend fun getRecipientProfile(uid: String): OperationResult<RecipientProfile> {
        val profile = savedProfiles[uid]
        return if (profile != null) {
            OperationResult.Success(profile)
        } else {
            OperationResult.Invalid("No recipient found with UID: $uid")
        }
    }

    override suspend fun saveRecipientProfile(profile: RecipientProfile): OperationResult<Unit> {
        if (shouldFailSave) {
            return OperationResult.Unavailable("Firestore save failed")
        }
        savedProfiles[profile.uid] = profile
        return OperationResult.Success(Unit)
    }
}

class ProfileRepairTest {

    @Test
    fun emptyDisplayNameIsRejectedImmediately() = runBlocking {
        val authAdapter = FakeAuthAdapterForRepair()
        val firestoreAdapter = FakeFirestoreAdapterForRepair()
        val sessionTracker = DefaultSessionTracker()
        val manager = AccountSessionManager(authAdapter, firestoreAdapter, sessionTracker)

        val res = manager.signUpAndCreateProfile("test@example.com", "password123", "   ")
        assertTrue(res is OperationResult.Invalid)
        assertEquals("Display name must not be blank.", (res as OperationResult.Invalid).message)
    }

    @Test
    fun failedProfileSaveReturnsUnavailableAndAllowsRepairWithoutReCreatingAuthAccount() = runBlocking {
        val authAdapter = FakeAuthAdapterForRepair()
        val firestoreAdapter = FakeFirestoreAdapterForRepair().apply { shouldFailSave = true }
        val sessionTracker = DefaultSessionTracker()
        val manager = AccountSessionManager(authAdapter, firestoreAdapter, sessionTracker)

        // Initial signup succeeds in Auth, but fails in Firestore
        val initialRes = manager.signUpAndCreateProfile("test@example.com", "password123", "User One")
        assertTrue(initialRes is OperationResult.Unavailable)

        // Fix Firestore connection and perform Profile Repair for the authenticated user
        firestoreAdapter.shouldFailSave = false
        val repairRes = manager.repairProfile("User One Repaired")
        assertTrue(repairRes is OperationResult.Success)

        val recipientRes = firestoreAdapter.getRecipientProfile("user-repair-123")
        assertTrue(recipientRes is OperationResult.Success)
        assertEquals("User One Repaired", (recipientRes as OperationResult.Success).data.displayName)
    }
}
