package com.example.timecapsule.data.media

import com.example.timecapsule.domain.model.OriginalPhotoFormat
import com.example.timecapsule.domain.model.OriginalPhotoLimits
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Tests byte retention and failure cleanup independently of Android's image decoder. */
class OriginalPhotoStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    /** Accepts the exact cap and preserves every byte and its SHA-256. */
    @Test fun exactBoundaryRetainsOriginal() = runBlocking {
        val bytes = ByteArray(OriginalPhotoLimits.MAX_BYTES.toInt()) { (it % 251).toByte() }
        val root = temporary.newFolder()
        val store = OriginalPhotoStore(root) { OriginalPhotoFormat.PNG }
        val result = store.retain("owner", "capsule", "image/png", bytes.inputStream())
        assertEquals(bytes.size.toLong(), result.sizeBytes)
        assertArrayEquals(bytes, root.walkTopDown().first { it.isFile }.readBytes())
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        assertEquals(hash, result.sha256)
    }

    /** Rejects an oversized actual stream and removes the incomplete copy. */
    @Test fun oversizedStreamIsRejected() = runBlocking {
        rejects(ByteArray(OriginalPhotoLimits.MAX_BYTES.toInt() + 1).inputStream())
    }

    /** Rejects an empty stream even if a provider claims it is an image. */
    @Test fun emptyStreamIsRejected() = runBlocking { rejects(byteArrayOf().inputStream()) }

    /** Rejects mismatched declared and decoded MIME types. */
    @Test fun mismatchedFormatIsRejected() = runBlocking {
        rejects(byteArrayOf(1).inputStream(), OriginalPhotoFormat.JPEG)
    }

    /** Rejects corrupt images when the decoder cannot produce a preview. */
    @Test fun unreadableImageIsRejected() = runBlocking {
        rejects(byteArrayOf(1).inputStream(), null)
    }

    /** Closes the stream and propagates a revoked-access failure without leaving a candidate. */
    @Test fun revokedPermissionClosesStreamAndCleansCopy() = runBlocking {
        val root = temporary.newFolder()
        var closed = false
        val input = object : InputStream() {
            /** Simulates revoked provider access during reading. */
            override fun read(): Int = throw SecurityException("revoked")
            /** Records stream ownership cleanup. */
            override fun close() { closed = true }
        }
        try {
            OriginalPhotoStore(root) { OriginalPhotoFormat.PNG }
                .retain("owner", "capsule", "image/png", input)
            fail("Expected permission failure")
        } catch (_: SecurityException) {
            assertTrue(closed)
            assertFalse(root.walkTopDown().any { it.isFile })
        }
    }

    /** Keeps a prior valid original when a replacement cannot be read. */
    @Test fun failedReplacementPreservesPreviousOriginal() = runBlocking {
        val root = temporary.newFolder()
        val store = OriginalPhotoStore(root) { OriginalPhotoFormat.PNG }
        val old = store.retain("owner", "capsule", "image/png", byteArrayOf(1).inputStream())
        val failing = object : InputStream() {
            /** Simulates a provider I/O failure. */
            override fun read(): Int = throw IOException("offline provider")
        }
        try {
            store.retain("owner", "capsule", "image/png", failing)
            fail("Expected read failure")
        } catch (_: IOException) {
            assertEquals(listOf(old.privateFileName), root.walkTopDown().filter { it.isFile }
                .map { it.name }.toList())
        }
    }

    /** Propagates cancellation and removes the interrupted candidate. */
    @Test fun cancellationIsNotConvertedToSuccess() = runBlocking {
        val root = temporary.newFolder()
        val input = object : InputStream() {
            /** Simulates interruption while the provider is reading. */
            override fun read(): Int = throw CancellationException("cancelled")
        }
        try {
            OriginalPhotoStore(root) { OriginalPhotoFormat.PNG }
                .retain("owner", "capsule", "image/png", input)
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertFalse(root.walkTopDown().any { it.isFile })
        }
    }

    /** Asserts expected invalid input is rejected and no incomplete original survives. */
    private suspend fun rejects(
        input: ByteArrayInputStream,
        decoded: OriginalPhotoFormat? = OriginalPhotoFormat.PNG
    ) {
        val root = temporary.newFolder()
        try {
            OriginalPhotoStore(root) { decoded }.retain("owner", "capsule", "image/png", input)
            fail("Expected invalid photo")
        } catch (_: InvalidOriginalPhotoException) {
            assertFalse(root.walkTopDown().any { it.isFile })
        }
    }
}
