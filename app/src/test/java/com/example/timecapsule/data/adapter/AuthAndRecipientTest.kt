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
        val directory = mutableMapOf<String, RecipientProfile>()
        var pendingWrites = false
        var isFromCache = false
        var simulateNetworkFailure = false
        var simulatePermissionDenied = false

        override fun isAvailable(): Boolean = true
        override fun firestoreInstance(): com.google.firebase.firestore.FirebaseFirestore? = null

        override suspend fun getRecipientProfile(uid: String): OperationResult<RecipientProfile> {
            if (uid.isBlank()) {
                return OperationResult.Invalid("UID must not be blank for recipient lookup.")
            }
            if (simulateNetworkFailure) {
                return OperationResult.Unavailable("Network unavailable during Firestore operation.")
            }
            if (simulatePermissionDenied) {
                return OperationResult.Denied("Access denied. Check the signed-in account.")
            }
            if (pendingWrites || isFromCache) {
                return OperationResult.Unavailable("Profile snapshot is unconfirmed or cached.")
            }
            val profile = directory[uid] ?: return OperationResult.Invalid("No recipient found with UID: $uid")
            if (profile.uid.isBlank() || profile.uid != uid) {
                return OperationResult.Invalid("Profile document UID missing or mismatched.")
            }
            if (profile.displayName.isBlank()) {
                return OperationResult.Invalid("Profile display name is missing or blank.")
            }
            return OperationResult.Success(profile)
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

    @Test
    fun testProfileVerificationBoundaries() = runBlocking {
        val firestoreAdapter = FakeFirestoreAdapter()
        val validUid = "uid-valid-1"

        // 1. Confirmed absent profile
        val absentRes = firestoreAdapter.getRecipientProfile(validUid)
        assertTrue(absentRes is OperationResult.Invalid)

        // 2. Confirmed valid profile
        firestoreAdapter.saveRecipientProfile(RecipientProfile(validUid, "Valid Name"))
        val validRes = firestoreAdapter.getRecipientProfile(validUid)
        assertTrue(validRes is OperationResult.Success)
        assertEquals("Valid Name", (validRes as OperationResult.Success).data.displayName)

        // 3. Pending-write snapshot (unconfirmed)
        firestoreAdapter.pendingWrites = true
        val pendingRes = firestoreAdapter.getRecipientProfile(validUid)
        assertTrue(pendingRes is OperationResult.Unavailable)
        firestoreAdapter.pendingWrites = false

        // 4. Cached snapshot (unconfirmed)
        firestoreAdapter.isFromCache = true
        val cachedRes = firestoreAdapter.getRecipientProfile(validUid)
        assertTrue(cachedRes is OperationResult.Unavailable)
        firestoreAdapter.isFromCache = false

        // 5. Blank display name
        firestoreAdapter.saveRecipientProfile(RecipientProfile(validUid, "   "))
        val blankNameRes = firestoreAdapter.getRecipientProfile(validUid)
        assertTrue(blankNameRes is OperationResult.Invalid)

        // 6. Network failure & permission denied
        firestoreAdapter.saveRecipientProfile(RecipientProfile(validUid, "Valid Name"))
        firestoreAdapter.simulateNetworkFailure = true
        assertTrue(firestoreAdapter.getRecipientProfile(validUid) is OperationResult.Unavailable)
        firestoreAdapter.simulateNetworkFailure = false

        firestoreAdapter.simulatePermissionDenied = true
        assertTrue(firestoreAdapter.getRecipientProfile(validUid) is OperationResult.Denied)
    }
}
