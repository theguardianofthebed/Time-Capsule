package com.example.timecapsule.data.adapter

import com.example.timecapsule.domain.model.OriginalPhotoFormat
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot
import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest

/**
 * Unit tests verifying [DefaultLocalRetentionAdapter] retention storage, verified markers,
 * offline read contracts, and account cleanup boundaries.
 */
class DefaultLocalRetentionAdapterTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var rootDir: File
    private lateinit var retentionAdapter: DefaultLocalRetentionAdapter

    private var active = SessionSnapshot(null, 1L)

    /** Switches the fixture account and captures its new generation. */
    private fun signIn(uid: String): SessionSnapshot {
        active = SessionSnapshot(uid, active.sessionGeneration + 1)
        return active
    }

    @Before
    fun setUp() {
        rootDir = temporaryFolder.newFolder("retained-content")
        retentionAdapter = DefaultLocalRetentionAdapter(rootDir) { active }
    }

    @After
    fun tearDown() {
        rootDir.deleteRecursively()
    }

    @Test
    fun testSaveAndReadTextOnlyRetainedContent() = runBlocking {
        val uid = "user-recipient-1"
        val capsuleId = "capsule-text-101"
        val unlockTime = Instant.parse("2026-11-25T12:00:00Z")

        val saveRes = retentionAdapter.saveRetainedContent(
            targetSession = signIn(uid),
            capsuleId = capsuleId,
            verifiedUnlockedAt = unlockTime,
            text = "Secret Message Text",
            photoBytes = null,
            photoFormat = null
        )
        assertTrue(saveRes is OperationResult.Success)

        val readRes = retentionAdapter.readRetainedContent(signIn(uid), capsuleId)
        assertTrue(readRes is OperationResult.Success)

        val content = (readRes as OperationResult.Success).data
        assertEquals(uid, content.uid)
        assertEquals(capsuleId, content.capsuleId)
        assertEquals("Secret Message Text", content.text)
        assertNull(content.photoBytes)
        assertNull(content.photoFormat)
        assertEquals(unlockTime, content.verifiedUnlockedAt)
    }

    @Test
    fun testSaveAndReadTextAndPhotoRetainedContent() = runBlocking {
        val uid = "user-recipient-2"
        val capsuleId = "capsule-photo-202"
        val unlockTime = Instant.parse("2026-11-25T14:30:00Z")
        val samplePhotoBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

        val saveRes = retentionAdapter.saveRetainedContent(
            targetSession = signIn(uid),
            capsuleId = capsuleId,
            verifiedUnlockedAt = unlockTime,
            text = "Capsule with Photo",
            photoBytes = samplePhotoBytes,
            photoFormat = OriginalPhotoFormat.PNG
        )
        assertTrue(saveRes is OperationResult.Success)

        val readRes = retentionAdapter.readRetainedContent(signIn(uid), capsuleId)
        assertTrue(readRes is OperationResult.Success)

        val content = (readRes as OperationResult.Success).data
        assertEquals("Capsule with Photo", content.text)
        assertNotNull(content.photoBytes)
        assertTrue(samplePhotoBytes.contentEquals(content.photoBytes!!))
        assertEquals(OriginalPhotoFormat.PNG, content.photoFormat)
    }

    @Test
    fun testReadDeniedForUnrelatedUserOrMissingMarker() = runBlocking {
        val uidA = "user-a"
        val uidB = "user-b"
        val capsuleId = "capsule-isolated-1"

        retentionAdapter.saveRetainedContent(
            targetSession = signIn(uidA),
            capsuleId = capsuleId,
            verifiedUnlockedAt = Instant.now(),
            text = "User A Private Content",
            photoBytes = null,
            photoFormat = null
        )

        // Reading with unrelated User B must be denied
        val readB = retentionAdapter.readRetainedContent(signIn(uidB), capsuleId)
        assertTrue(readB is OperationResult.Denied)

        // Reading non-existent capsule for User A must be denied
        val readNonExistent = retentionAdapter.readRetainedContent(signIn(uidA), "capsule-nonexistent")
        assertTrue(readNonExistent is OperationResult.Denied)
    }

    @Test
    fun testReadUnavailableIfRequiredContentFileIsMissing() = runBlocking {
        val uid = "user-recipient-3"
        val capsuleId = "capsule-corrupt-303"

        retentionAdapter.saveRetainedContent(
            targetSession = signIn(uid),
            capsuleId = capsuleId,
            verifiedUnlockedAt = Instant.now(),
            text = "Text to delete",
            photoBytes = null,
            photoFormat = null
        )

        // Manually delete the content.text file to simulate data corruption
        val accountHash = MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val textFile = File(rootDir, "$accountHash/$capsuleId/content.text")
        assertTrue(textFile.exists())
        textFile.delete()

        val readRes = retentionAdapter.readRetainedContent(signIn(uid), capsuleId)
        assertTrue(readRes is OperationResult.Unavailable)
    }

    @Test
    fun testClearAccountDataIsolatesOtherUsers() = runBlocking {
        val uidA = "user-account-a"
        val uidB = "user-account-b"

        retentionAdapter.saveRetainedContent(signIn(uidA), "capsule-a", Instant.now(), "Content A", null, null)
        retentionAdapter.saveRetainedContent(signIn(uidB), "capsule-b", Instant.now(), "Content B", null, null)

        // Clear Account A
        retentionAdapter.clearAccountData(uidA)

        // Account A content must be gone (Denied)
        val readA = retentionAdapter.readRetainedContent(signIn(uidA), "capsule-a")
        assertTrue(readA is OperationResult.Denied)

        // Account B content must remain intact
        val readB = retentionAdapter.readRetainedContent(signIn(uidB), "capsule-b")
        assertTrue(readB is OperationResult.Success)
    }

    @Test
    fun testClearAllDataErasesRoot() = runBlocking {
        val uid = "user-account-c"
        retentionAdapter.saveRetainedContent(signIn(uid), "capsule-c", Instant.now(), "Content C", null, null)

        retentionAdapter.clearAllData()

        val readC = retentionAdapter.readRetainedContent(signIn(uid), "capsule-c")
        assertTrue(readC is OperationResult.Denied)
    }

    @Test
    fun testSessionValidationEnforcesActiveSession() = runBlocking {
        var activeSession = SessionSnapshot("user-session-1", 1L)
        val first = activeSession
        val adapterWithSession = DefaultLocalRetentionAdapter(rootDir, session = { activeSession })

        val saveRes = adapterWithSession.saveRetainedContent(
            first, "capsule-s1", Instant.now(), "Content S1", null, null
        )
        assertTrue(saveRes is OperationResult.Success)

        // Switch active session to another user
        activeSession = SessionSnapshot("user-session-2", 2L)

        // Read for user-session-1 must be denied
        val readRes = adapterWithSession.readRetainedContent(first, "capsule-s1")
        assertTrue(readRes is OperationResult.Denied)

        // Save for user-session-1 must be denied
        val saveDenied = adapterWithSession.saveRetainedContent(
            first, "capsule-s2", Instant.now(), "Content S2", null, null
        )
        assertTrue(saveDenied is OperationResult.Denied)
    }

    @Test
    fun testMalformedMarkerFlagsReturnDenied() = runBlocking {
        val uid = "user-flag-test"
        val capsuleId = "capsule-flag-1"

        retentionAdapter.saveRetainedContent(signIn(uid), capsuleId, Instant.now(), "Text Flag", null, null)

        val accountHash = MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val markerFile = File(rootDir, "$accountHash/$capsuleId/verified-marker.properties")
        assertTrue(markerFile.exists())

        // Overwrite marker file with corrupt flags (both false)
        markerFile.writeText("version=1\nuid=$uid\ncapsuleId=$capsuleId\nverifiedUnlockedAt=${Instant.now()}\nhasText=false\nhasPhoto=false\n")

        val readRes = retentionAdapter.readRetainedContent(signIn(uid), capsuleId)
        assertTrue(readRes is OperationResult.Denied)
    }
}
