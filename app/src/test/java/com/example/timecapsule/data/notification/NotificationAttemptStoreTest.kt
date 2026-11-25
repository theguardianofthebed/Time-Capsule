package com.example.timecapsule.data.notification

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [NotificationAttemptStore] verifying persistent state transitions,
 * account isolation, atomic claiming, storage write failure handling, and store reconstruction survival.
 */
class NotificationAttemptStoreTest {

    private lateinit var tempFile: File
    private lateinit var store: NotificationAttemptStore

    @Before
    fun setUp() {
        tempFile = File.createTempFile("notification_attempts_test", ".json")
        store = NotificationAttemptStore(tempFile)
    }

    @After
    fun tearDown() {
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }

    @Test
    fun `getAttemptState returns ABSENT initially`() {
        val state = store.getAttemptState("user-1", "capsule-101")
        assertEquals(AttemptState.ABSENT, state)
    }

    @Test
    fun `recordPendingPermission stores PENDING_PERMISSION state`() {
        store.recordPendingPermission("user-1", "capsule-101")
        val state = store.getAttemptState("user-1", "capsule-101")
        assertEquals(AttemptState.PENDING_PERMISSION, state)
    }

    @Test
    fun `atomicallyClaimAttempt claims ABSENT candidate and returns true`() {
        val claimed = store.atomicallyClaimAttempt("user-1", "capsule-101")
        assertTrue(claimed)
        assertEquals(AttemptState.ATTEMPTED, store.getAttemptState("user-1", "capsule-101"))
    }

    @Test
    fun `atomicallyClaimAttempt claims PENDING_PERMISSION candidate and returns true`() {
        store.recordPendingPermission("user-1", "capsule-101")
        val claimed = store.atomicallyClaimAttempt("user-1", "capsule-101")
        assertTrue(claimed)
        assertEquals(AttemptState.ATTEMPTED, store.getAttemptState("user-1", "capsule-101"))
    }

    @Test
    fun `atomicallyClaimAttempt rejects duplicate claim when already ATTEMPTED`() {
        assertTrue(store.atomicallyClaimAttempt("user-1", "capsule-101"))
        assertFalse(store.atomicallyClaimAttempt("user-1", "capsule-101"))
    }

    @Test
    fun `persistent markers survive store reconstruction`() {
        store.recordPendingPermission("user-1", "capsule-101")
        assertTrue(store.atomicallyClaimAttempt("user-1", "capsule-102"))

        // Reconstruct store instance from same underlying file
        val reloadedStore = NotificationAttemptStore(tempFile)
        assertEquals(AttemptState.PENDING_PERMISSION, reloadedStore.getAttemptState("user-1", "capsule-101"))
        assertEquals(AttemptState.ATTEMPTED, reloadedStore.getAttemptState("user-1", "capsule-102"))
    }

    @Test
    fun `write failure preserves previous state and returns false`() {
        val unwritableFile = File(tempFile.parentFile, "unwritable_dir_test/store.json")
        // Create file inside a non-directory file to trigger write failure
        val parentAsFile = File(tempFile.parentFile, "unwritable_dir_test")
        parentAsFile.createNewFile()
        parentAsFile.setWritable(false)

        try {
            val unwritableStore = NotificationAttemptStore(unwritableFile)
            val claimed = unwritableStore.atomicallyClaimAttempt("user-1", "capsule-101")
            assertFalse("Write failure must return false on attempt claim", claimed)
            assertEquals(AttemptState.ABSENT, unwritableStore.getAttemptState("user-1", "capsule-101"))
        } finally {
            parentAsFile.setWritable(true)
            parentAsFile.delete()
        }
    }

    @Test
    fun `corrupted or malformed file fails claims closed and preserves file`() {
        val corruptFile = File.createTempFile("corrupt_attempts", ".json")
        try {
            corruptFile.writeText("invalid_key_without_equals_and_corrupt_state", Charsets.UTF_8)
            val corruptStore = NotificationAttemptStore(corruptFile)

            assertFalse("Claim must fail closed on corrupted journal file", corruptStore.atomicallyClaimAttempt("user-1", "capsule-101"))
            assertFalse("Pending permission record must fail closed on corrupted file", corruptStore.recordPendingPermission("user-1", "capsule-101"))
            assertEquals(AttemptState.ABSENT, corruptStore.getAttemptState("user-1", "capsule-101"))

            // Ensure clearAccount does NOT overwrite the corrupted file on disk
            corruptStore.clearAccount("user-1")
            assertEquals("invalid_key_without_equals_and_corrupt_state", corruptFile.readText(Charsets.UTF_8))
        } finally {
            if (corruptFile.exists()) corruptFile.delete()
        }
    }

    @Test
    fun `clearAccount removes markers for target account only`() {
        store.atomicallyClaimAttempt("user-1", "capsule-101")
        store.atomicallyClaimAttempt("user-2", "capsule-101")

        store.clearAccount("user-1")

        assertEquals(AttemptState.ABSENT, store.getAttemptState("user-1", "capsule-101"))
        assertEquals(AttemptState.ATTEMPTED, store.getAttemptState("user-2", "capsule-101"))
    }

    @Test
    fun `blank identifiers return ABSENT and return false on claim`() {
        assertEquals(AttemptState.ABSENT, store.getAttemptState("", "capsule-101"))
        assertFalse(store.atomicallyClaimAttempt(" ", "capsule-101"))
        assertFalse(store.atomicallyClaimAttempt("user-1", " "))
    }
}
