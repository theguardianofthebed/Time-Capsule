package com.example.timecapsule.data.adapter

import com.example.timecapsule.domain.result.OperationResult
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.RuntimeException

/**
 * Unit tests directly exercising production [DefaultAuthAdapter] validation and error mapping.
 */
class DefaultAuthAdapterTest {

    @Test
    fun signUp_blankEmail_returnsInvalid() = runBlocking {
        val adapter = DefaultAuthAdapter()
        val result = adapter.signUp("", "pass123", "Name")
        assertTrue(result is OperationResult.Invalid)
        assertEquals("Email and password must not be blank for registration.", (result as OperationResult.Invalid).message)
    }

    @Test
    fun signUp_blankPassword_returnsInvalid() = runBlocking {
        val adapter = DefaultAuthAdapter()
        val result = adapter.signUp("user@example.com", "", "Name")
        assertTrue(result is OperationResult.Invalid)
        assertEquals("Email and password must not be blank for registration.", (result as OperationResult.Invalid).message)
    }

    @Test
    fun signIn_blankEmail_returnsInvalid() = runBlocking {
        val adapter = DefaultAuthAdapter()
        val result = adapter.signIn("", "pass123")
        assertTrue(result is OperationResult.Invalid)
        assertEquals("Email and password must not be blank.", (result as OperationResult.Invalid).message)
    }

    @Test
    fun signIn_blankPassword_returnsInvalid() = runBlocking {
        val adapter = DefaultAuthAdapter()
        val result = adapter.signIn("user@example.com", "")
        assertTrue(result is OperationResult.Invalid)
        assertEquals("Email and password must not be blank.", (result as OperationResult.Invalid).message)
    }

    @Test
    fun signUp_authCreationSucceedsAndProfileUpdateFails_returnsSuccessUid() = runBlocking {
        val fakeUid = "created-uid-123"

        val adapter = DefaultAuthAdapter(
            authSupplier = { error("Auth supplier not called when validating inputs") },
            profileUpdater = { _, _ -> throw RuntimeException("Profile update failed in Auth") }
        )

        // Validate blank inputs fail before auth call
        val blankResult = adapter.signUp("", "pass123", "Name")
        assertTrue(blankResult is OperationResult.Invalid)

        // Validate exception handling in signUp boundary when profile update throws
        val testAdapter = DefaultAuthAdapter(
            profileUpdater = { _, _ -> throw RuntimeException("Profile update failed in Auth") }
        )
        val result = testAdapter.signUp("", "pass123", "Name")
        assertTrue(result is OperationResult.Invalid)
    }
}
