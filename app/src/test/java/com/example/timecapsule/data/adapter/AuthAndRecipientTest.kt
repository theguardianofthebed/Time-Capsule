package com.example.timecapsule.data.adapter

import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.result.OperationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying Issue #10 Auth and RecipientProfile operations.
 */
class AuthAndRecipientTest {

    private class FakeAuthAdapter : AuthAdapter {
        var currentUid: String? = null
        var currentEmail: String? = null
        var currentDisplayName: String? = null

        override fun activeUid(): String? = currentUid
        override fun activeEmail(): String? = currentEmail
        override fun activeDisplayName(): String? = currentDisplayName

        override suspend fun signIn(email: String, password: String): OperationResult<String> {
            if (email.isBlank() || password.isBlank()) {
                return OperationResult.Invalid("Email and password must not be blank.")
            }
            currentUid = "fake-uid-123"
            currentEmail = email
            currentDisplayName = "User Test"
            return OperationResult.Success("fake-uid-123")
        }

        override suspend fun signUp(
            email: String,
            password: String,
            displayName: String
        ): OperationResult<String> {
            if (email.isBlank() || password.isBlank()) {
                return OperationResult.Invalid("Email and password must not be blank for registration.")
            }
            currentUid = "fake-uid-123"
            currentEmail = email
            currentDisplayName = displayName
            return OperationResult.Success("fake-uid-123")
        }

        override suspend fun updateDisplayName(displayName: String): OperationResult<Unit> {
            if (displayName.isBlank()) {
                return OperationResult.Invalid("Display name must not be blank.")
            }
            if (currentUid == null) {
                return OperationResult.Denied("No active authenticated session available.")
            }
            currentDisplayName = displayName
            return OperationResult.Success(Unit)
        }

        override fun signOut() {
            currentUid = null
            currentEmail = null
            currentDisplayName = null
        }
    }

    private class FakeFirestoreAdapter : FirestoreAdapter {
        private val directory = mutableMapOf<String, RecipientProfile>()

        override fun isAvailable(): Boolean = true
        override fun firestoreInstance(): com.google.firebase.firestore.FirebaseFirestore? = null

        override suspend fun getRecipientProfile(uid: String): OperationResult<RecipientProfile> {
            if (uid.isBlank()) {
                return OperationResult.Invalid("UID must not be blank for recipient lookup.")
            }
            val profile = directory[uid]
            return if (profile != null) {
                OperationResult.Success(profile)
            } else {
                OperationResult.Invalid("No recipient found with UID: $uid")
            }
        }

        override suspend fun saveRecipientProfile(profile: RecipientProfile): OperationResult<Unit> {
            if (profile.uid.isBlank()) {
                return OperationResult.Invalid("User UID must not be blank when saving profile.")
            }
            directory[profile.uid] = profile
            return OperationResult.Success(Unit)
        }
    }

    @Test
    fun testSignInAndProfileOperations() = runBlocking {
        val authAdapter = FakeAuthAdapter()
        val firestoreAdapter = FakeFirestoreAdapter()

        // 1. Invalid sign in
        val invalidResult = authAdapter.signIn("", "")
        assertTrue(invalidResult is OperationResult.Invalid)

        // 2. Successful sign up
        val signUpResult = authAdapter.signUp("test@example.com", "password123", "User G")
        assertTrue(signUpResult is OperationResult.Success)
        val uid = (signUpResult as OperationResult.Success).data
        assertEquals("fake-uid-123", uid)

        // 3. Save profile to recipientDirectory
        val saveResult = firestoreAdapter.saveRecipientProfile(RecipientProfile(uid, "User G"))
        assertTrue(saveResult is OperationResult.Success)

        // 4. Exact lookup profile
        val lookupResult = firestoreAdapter.getRecipientProfile(uid)
        assertTrue(lookupResult is OperationResult.Success)
        val profile = (lookupResult as OperationResult.Success).data
        assertEquals("User G", profile.displayName)

        // 5. Sign out
        authAdapter.signOut()
        assertEquals(null, authAdapter.activeUid())
    }
}
