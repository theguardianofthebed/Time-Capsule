package com.example.timecapsule.data.adapter

import com.example.timecapsule.domain.result.OperationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
