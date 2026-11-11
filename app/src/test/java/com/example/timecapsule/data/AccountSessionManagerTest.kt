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
import org.junit.Assert.assertNull
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
            val newUid = if (email.contains("userB")) "user-b" else "user-123"
            uid = newUid
            this.email = email
            this.displayName = "User $newUid"
            return OperationResult.Success(newUid)
        }

        override suspend fun signUp(
            email: String,
            password: String,
            displayName: String
        ): OperationResult<String> {
            if (email.isBlank() || password.isBlank()) {
                return OperationResult.Invalid("Email and password must not be blank.")
            }
            val newUid = "user-123"
            uid = newUid
            this.email = email
            this.displayName = displayName
            return OperationResult.Success(newUid)
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
    fun testStaleCallbackFromUserADoesNotSignOutValidUserB() = runBlocking {
        val delayedAuth = object : AuthAdapter by fakeAuthAdapter {
            override suspend fun signIn(email: String, password: String): OperationResult<String> {
                delay(50)
                return OperationResult.Success("user-a")
            }
        }
        val manager = AccountSessionManager(delayedAuth, fakeFirestoreAdapter, sessionTracker)

        // User A starts sign in
        val jobA = async { manager.signIn("userA@example.com", "passA") }

        // User B signs in immediately after, updating sessionTracker to user-b
        delay(10)
        sessionTracker.startSession("user-b")
        fakeAuthAdapter.uid = "user-b"

        // Delayed sign in for User A finishes
        val resultA = jobA.await()

        assertTrue("User A's delayed sign in must be denied as stale", resultA is OperationResult.Denied)
        assertTrue("User B's session must remain valid", sessionTracker.currentSession().isAuthenticated)
        assertEquals("user-b", sessionTracker.currentSession().uid)
        assertEquals("user-b", fakeAuthAdapter.activeUid())
    }

    @Test
    fun testDelayedAccountASignInWithSdkSideEffectCleansUpInconsistentState() = runBlocking {
        val delayedAuth = object : AuthAdapter by fakeAuthAdapter {
            override suspend fun signIn(email: String, password: String): OperationResult<String> {
                delay(50)
                fakeAuthAdapter.uid = "user-a" // Mimic SDK side effect mutating active UID to user-a
                return OperationResult.Success("user-a")
            }
        }
        val manager = AccountSessionManager(delayedAuth, fakeFirestoreAdapter, sessionTracker)

        // User A starts delayed sign-in
        val jobA = async { manager.signIn("userA@example.com", "passA") }

        // While User A is in-flight, SessionTracker is invalidated by a sign-out
        delay(10)
        sessionTracker.clearSession()

        val resultA = jobA.await()

        assertTrue("Stale User A sign-in must be denied", resultA is OperationResult.Denied)
        assertNull("Stale SDK active UID must be signed out from Auth", fakeAuthAdapter.activeUid())
        assertFalse("SessionTracker must remain unauthenticated", sessionTracker.currentSession().isAuthenticated)
    }

    @Test
    fun testLogoutOccursWhileSignInIsInProgress() = runBlocking {
        val delayedAuth = object : AuthAdapter by fakeAuthAdapter {
            override suspend fun signIn(email: String, password: String): OperationResult<String> {
                delay(50)
                fakeAuthAdapter.uid = "user-in-flight"
                return OperationResult.Success("user-in-flight")
            }
        }
        val manager = AccountSessionManager(delayedAuth, fakeFirestoreAdapter, sessionTracker)

        val job = async { manager.signIn("user@example.com", "pass123") }

        delay(10)
        manager.signOut()

        val result = job.await()
        assertTrue("Sign-in must be denied when sign-out occurs during execution", result is OperationResult.Denied)
        assertNull("Auth must be signed out", fakeAuthAdapter.activeUid())
        assertFalse("SessionTracker must be unauthenticated", sessionTracker.currentSession().isAuthenticated)
    }

    @Test
    fun testSignUpFinishesAfterOriginalSessionInvalidated() = runBlocking {
        val delayedAuth = object : AuthAdapter by fakeAuthAdapter {
            override suspend fun signUp(email: String, password: String, displayName: String): OperationResult<String> {
                delay(50)
                fakeAuthAdapter.uid = "user-signup-stale"
                return OperationResult.Success("user-signup-stale")
            }
        }
        val manager = AccountSessionManager(delayedAuth, fakeFirestoreAdapter, sessionTracker)

        val job = async { manager.signUpAndCreateProfile("signup@example.com", "pass123", "New User") }

        delay(10)
        sessionTracker.clearSession()

        val result = job.await()
        assertTrue("Stale sign-up must be denied", result is OperationResult.Denied)
        assertNull("Auth must be signed out for stale sign-up", fakeAuthAdapter.activeUid())
        assertFalse("SessionTracker must remain unauthenticated", sessionTracker.currentSession().isAuthenticated)
    }

    @Test
    fun testCurrentAccountRemainsConsistentBetweenAuthAndSessionTracker() = runBlocking {
        val res = accountSessionManager.signIn("user@example.com", "validPass")
        assertTrue(res is OperationResult.Success)

        assertEquals(fakeAuthAdapter.activeUid(), sessionTracker.currentSession().uid)
        assertTrue(sessionTracker.currentSession().isAuthenticated)

        accountSessionManager.signOut()
        assertEquals(fakeAuthAdapter.activeUid(), sessionTracker.currentSession().uid)
        assertFalse(sessionTracker.currentSession().isAuthenticated)
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
