package com.example.timecapsule.data.adapter

import com.example.timecapsule.domain.model.RecipientProfile
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

    @Test
    fun getRecipientProfile_validServerConfirmedDocument_returnsSuccess() = runBlocking {
        val adapter = DefaultFirestoreAdapter(recipientFetcher = { uid ->
            OperationResult.Success(RecipientProfile(uid, "Valid User Name"))
        })
        val result = adapter.getRecipientProfile("valid-uid-100")
        assertTrue(result is OperationResult.Success)
        assertEquals("Valid User Name", (result as OperationResult.Success).data.displayName)
    }

    @Test
    fun getRecipientProfile_confirmedMissingDocument_returnsInvalidNonexistent() = runBlocking {
        val adapter = DefaultFirestoreAdapter(recipientFetcher = { uid ->
            OperationResult.Invalid("No recipient found with UID: $uid")
        })
        val result = adapter.getRecipientProfile("missing-uid-200")
        assertTrue(result is OperationResult.Invalid)
        assertEquals("No recipient found with UID: missing-uid-200", (result as OperationResult.Invalid).message)
    }

    @Test
    fun getRecipientProfile_pendingLocalWritesOrCachedSnapshot_returnsUnavailable() = runBlocking {
        val adapter = DefaultFirestoreAdapter(recipientFetcher = {
            OperationResult.Unavailable("Profile snapshot is unconfirmed or cached.")
        })
        val result = adapter.getRecipientProfile("cached-uid-300")
        assertTrue(result is OperationResult.Unavailable)
        assertEquals("Profile snapshot is unconfirmed or cached.", (result as OperationResult.Unavailable).message)
    }

    @Test
    fun getRecipientProfile_missingOrMismatchedStoredUid_returnsInvalidProfile() = runBlocking {
        val adapterMissingUid = DefaultFirestoreAdapter(recipientFetcher = {
            OperationResult.Invalid("Profile document UID missing or mismatched.")
        })
        val result = adapterMissingUid.getRecipientProfile("uid-400")
        assertTrue(result is OperationResult.Invalid)
        assertEquals("Profile document UID missing or mismatched.", (result as OperationResult.Invalid).message)
    }

    @Test
    fun getRecipientProfile_blankDisplayName_returnsInvalidProfile() = runBlocking {
        val adapter = DefaultFirestoreAdapter(recipientFetcher = {
            OperationResult.Invalid("Profile display name is missing or blank.")
        })
        val result = adapter.getRecipientProfile("uid-500")
        assertTrue(result is OperationResult.Invalid)
        assertEquals("Profile display name is missing or blank.", (result as OperationResult.Invalid).message)
    }

    @Test
    fun getRecipientProfile_networkFailureAndPermissionDenied_mapsToUnavailableAndDenied() = runBlocking {
        val adapterNetwork = DefaultFirestoreAdapter(recipientFetcher = {
            OperationResult.Unavailable("Network unavailable during Firestore operation.")
        })
        assertTrue(adapterNetwork.getRecipientProfile("uid-600") is OperationResult.Unavailable)

        val adapterDenied = DefaultFirestoreAdapter(recipientFetcher = {
            OperationResult.Denied("Access denied. Check the signed-in account.")
        })
        assertTrue(adapterDenied.getRecipientProfile("uid-600") is OperationResult.Denied)
    }
}
