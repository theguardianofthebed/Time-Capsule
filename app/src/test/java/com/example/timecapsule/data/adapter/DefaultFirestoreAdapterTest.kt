package com.example.timecapsule.data.adapter

import com.example.timecapsule.domain.result.OperationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests directly exercising production [DefaultFirestoreAdapter] validation and error mapping.
 */
class DefaultFirestoreAdapterTest {

    @Test
    fun getRecipientProfile_blankUid_returnsInvalid() = runBlocking {
        val adapter = DefaultFirestoreAdapter()
        val result = adapter.getRecipientProfile("")
        assertTrue(result is OperationResult.Invalid)
        assertEquals("UID must not be blank for recipient lookup.", (result as OperationResult.Invalid).message)
    }

    @Test
    fun getRecipientProfile_nullFirestoreInstance_returnsUnavailable() = runBlocking {
        val adapter = DefaultFirestoreAdapter(contextSupplier = { null })
        val result = adapter.getRecipientProfile("user-uid-123")
        assertTrue(result is OperationResult.Unavailable)
        assertEquals("Firestore service is unavailable.", (result as OperationResult.Unavailable).message)
    }
}
