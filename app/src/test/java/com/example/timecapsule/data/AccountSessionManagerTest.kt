package com.example.timecapsule.data

import com.example.timecapsule.data.adapter.AuthAdapter
import com.example.timecapsule.data.adapter.FirestoreAdapter
import com.example.timecapsule.data.session.DefaultSessionTracker
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.result.OperationResult
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AccountSessionManager], session generation single updates,
 * delayed authentication stale callback rejection, and Profile Repair.
 */
class AccountSessionManagerTest {

    private lateinit var sessionTracker: DefaultSessionTracker
    private lateinit var fakeAuthAdapter: FakeAuthAdapter
    private lateinit var fakeFirestoreAdapter: FakeFirestoreAdapter
    private lateinit var accountSessionManager: AccountSessionManager

    private class FakeAuthAdapter : AuthAdapter {
        var uid: String? = null
        var email: String? = null
        var displayName: String? = null

        override fun activeUid(): String? = uid
        override fun activeEmail(): String? = email
        override fun activeDisplayName(): String? = displayName

        override suspend fun signIn(email: String, password: String): OperationResult<String> {
            if (email.isBlank() || password.isBlank()) {
                return OperationResult.Invalid("Email and password must not be blank.")
            }
            if (password == "wrong") {
                return OperationResult.Invalid("Invalid credentials provided.")
            }
            uid = "user-123"
            this.email = email
            this.displayName = "Test User"
            return OperationResult.Success("user-123")
        }

        override suspend fun signUp(
            email: String,
            password: String,
            displayName: String
        ): OperationResult<String> {
            if (email.isBlank() || password.isBlank()) {
                return OperationResult.Invalid("Email and password must not be blank.")
            }
            uid = "user-123"
            this.email = email
            this.displayName = displayName
            return OperationResult.Success("user-123")
        }

        override suspend fun updateDisplayName(displayName: String): OperationResult<Unit> {
            if (uid == null) {
                return OperationResult.Denied("No active authenticated session available.")
            }
            this.displayName = displayName
            return OperationResult.Success(Unit)
        }

        override fun signOut() {
            uid = null
            email = null
            displayName = null
        }
    }

    private class FakeFirestoreAdapter : FirestoreAdapter {
        val directory = mutableMapOf<String, RecipientProfile>()

        override fun isAvailable(): Boolean = true
        override fun firestoreInstance(): FirebaseFirestore? = null

        override suspend fun getRecipientProfile(uid: String): OperationResult<RecipientProfile> {
            val profile = directory[uid]
            return if (profile != null) {
                OperationResult.Success(profile)
            } else {
                OperationResult.Invalid("No recipient found with UID: $uid")
            }
        }

        override suspend fun saveRecipientProfile(profile: RecipientProfile): OperationResult<Unit> {
            directory[profile.uid] = profile
            return OperationResult.Success(Unit)
        }
    }

    @Before
    fun setUp() {
        sessionTracker = DefaultSessionTracker()
        fakeAuthAdapter = FakeAuthAdapter()
        fakeFirestoreAdapter = FakeFirestoreAdapter()
        accountSessionManager = AccountSessionManager(
            authAdapter = fakeAuthAdapter,
            firestoreAdapter = fakeFirestoreAdapter,
            sessionTracker = sessionTracker
        )
    }

    @Test
    fun testSignInUpdatesSessionTrackerExactlyOnce() = runBlocking {
        assertEquals(1L, sessionTracker.currentSession().sessionGeneration)
        assertFalse(sessionTracker.currentSession().isAuthenticated)

        val result = accountSessionManager.signIn("user@example.com", "validPass")
        assertTrue(result is OperationResult.Success)

        // SessionTracker must be updated to authenticated state with incremented generation
        assertEquals(2L, sessionTracker.currentSession().sessionGeneration)
        assertTrue(sessionTracker.currentSession().isAuthenticated)
        assertEquals("user-123", sessionTracker.currentSession().uid)
    }

    @Test
    fun testSignUpAndProfileCreationStartsSessionAndSavesProfile() = runBlocking {
        val result = accountSessionManager.signUpAndCreateProfile("new@example.com", "pass123", "New User")
        assertTrue(result is OperationResult.Success)

        // Session started
        assertEquals(2L, sessionTracker.currentSession().sessionGeneration)
        assertEquals("user-123", sessionTracker.currentSession().uid)

        // Firestore profile created
        val profileRes = fakeFirestoreAdapter.getRecipientProfile("user-123")
        assertTrue(profileRes is OperationResult.Success)
        assertEquals("New User", (profileRes as OperationResult.Success).data.displayName)
    }

    @Test
    fun testUnauthenticatedProfileRepairIsDenied() = runBlocking {
        // Unauthenticated session
        sessionTracker.clearSession()
        fakeAuthAdapter.signOut()

        val repairRes = accountSessionManager.repairProfile("Repaired Name")
        assertTrue(repairRes is OperationResult.Denied)
    }

    @Test
    fun testAuthenticatedProfileRepairSucceeds() = runBlocking {
        // Authenticated session
        accountSessionManager.signIn("user@example.com", "pass123")

        val repairRes = accountSessionManager.repairProfile("Repaired Name")
        assertTrue(repairRes is OperationResult.Success)

        // Check profile updated in Auth and Firestore
        assertEquals("Repaired Name", fakeAuthAdapter.activeDisplayName())
        val profileRes = fakeFirestoreAdapter.getRecipientProfile("user-123")
        assertTrue(profileRes is OperationResult.Success)
        assertEquals("Repaired Name", (profileRes as OperationResult.Success).data.displayName)
    }

    @Test
    fun testDelayedSignInIsRejectedIfSessionChangesDuringAuthentication() = runBlocking {
        sessionTracker.startSession("user-a")

        val job = async {
            val delayedAuth = object : AuthAdapter by fakeAuthAdapter {
                override suspend fun signIn(email: String, password: String): OperationResult<String> {
                    delay(50)
                    return fakeAuthAdapter.signIn(email, password)
                }
            }
            val delayedManager = AccountSessionManager(delayedAuth, fakeFirestoreAdapter, sessionTracker)
            delayedManager.signIn("user@example.com", "validPass")
        }

        // Session cleared/changed while sign-in was in-flight
        delay(10)
        sessionTracker.clearSession()

        val result = job.await()
        assertTrue("Delayed sign-in must be rejected when session changes during operation", result is OperationResult.Denied)
    }

    @Test
    fun testSignOutClearsSessionTrackerWithoutDuplicateCalls() {
        runBlocking {
            accountSessionManager.signIn("user@example.com", "pass123")
            val initialGen = sessionTracker.currentSession().sessionGeneration

            accountSessionManager.signOut()

            assertFalse(sessionTracker.currentSession().isAuthenticated)
            assertEquals(initialGen + 1, sessionTracker.currentSession().sessionGeneration)
        }
    }
}
