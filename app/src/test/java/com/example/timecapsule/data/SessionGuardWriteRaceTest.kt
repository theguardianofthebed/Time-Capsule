package com.example.timecapsule.data

import com.example.timecapsule.data.adapter.DefaultLocalRetentionAdapter
import com.example.timecapsule.data.session.DefaultSessionTracker
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot
import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests verifying that delayed old-session writes are rejected across account transitions
 * and same-UID re-authentication (A -> logout -> A) generation changes.
 */
class SessionGuardWriteRaceTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var rootDir: File
    private lateinit var sessionTracker: DefaultSessionTracker
    private lateinit var retentionAdapter: DefaultLocalRetentionAdapter

    @Before
    fun setUp() {
        rootDir = temporaryFolder.newFolder("retained-content")
        sessionTracker = DefaultSessionTracker()
        retentionAdapter = DefaultLocalRetentionAdapter(
            root = rootDir,
            session = { sessionTracker.currentSession() }
        )
    }

    @After
    fun tearDown() {
        temporaryFolder.delete()
    }

    @Test
    fun testSameUidReauthenticationInvalidatesPreviousGeneration() = runBlocking {
        // Gen 1: User A signs in
        sessionTracker.startSession("user-a")
        val gen1Session = sessionTracker.currentSession()
        assertEquals("user-a", gen1Session.uid)

        // Save content under Gen 1
        val saveGen1 = retentionAdapter.saveRetainedContent(
            targetSession = gen1Session,
            capsuleId = "capsule-gen1",
            verifiedUnlockedAt = Instant.now(),
            text = "Gen 1 Text",
            photoBytes = null,
            photoFormat = null
        )
        assertTrue(saveGen1 is OperationResult.Success)

        // Logout and Re-authenticate User A (Gen 2)
        sessionTracker.clearSession()
        sessionTracker.startSession("user-a")
        val gen2Session = sessionTracker.currentSession()
        assertEquals("user-a", gen2Session.uid)
        assertTrue(gen2Session.sessionGeneration > gen1Session.sessionGeneration)

        // Gen 1 session snapshot is no longer valid
        assertFalse(sessionTracker.isSessionValid(gen1Session))
        assertTrue(sessionTracker.isSessionValid(gen2Session))

        // Attempting to read using Gen 1 target session fails authorization
        val readGen1 = retentionAdapter.readRetainedContent(gen1Session, "capsule-gen1")
        assertTrue("Read with gen1Session must be denied", readGen1 is OperationResult.Denied)

        // Current active session (Gen 2) permits read for active account
        val readGen2 = retentionAdapter.readRetainedContent(gen2Session, "capsule-gen1")
        assertTrue("Read with gen2Session must succeed", readGen2 is OperationResult.Success)
    }

    @Test
    fun testAccountSwitchRejectsDelayedOldSessionWrite() = runBlocking {
        sessionTracker.startSession("user-a")

        val gen1Session = sessionTracker.currentSession()

        // Switch account to User B
        sessionTracker.clearSession()
        sessionTracker.startSession("user-b")

        // Delayed write capturing User A's old session must be denied
        val delayedWrite = retentionAdapter.saveRetainedContent(
            targetSession = gen1Session,
            capsuleId = "capsule-delayed",
            verifiedUnlockedAt = Instant.now(),
            text = "Delayed Write Text",
            photoBytes = null,
            photoFormat = null
        )
        assertTrue(delayedWrite is OperationResult.Denied)
    }
}
