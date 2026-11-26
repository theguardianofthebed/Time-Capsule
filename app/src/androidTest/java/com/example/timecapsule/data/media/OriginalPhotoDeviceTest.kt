package com.example.timecapsule.data.media

import com.example.timecapsule.data.session.fixtureSession
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.timecapsule.domain.model.OriginalPhotoFormat
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android decoder tests; prepared for a phone and not claimed as executed by JVM tests. */
@RunWith(AndroidJUnit4::class)
class OriginalPhotoDeviceTest {
    /** Creates a tiny real image using Android's encoder. */
    private fun image(format: Bitmap.CompressFormat): ByteArray {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        return try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(format, 100, output))
                output.toByteArray()
            }
        } finally { bitmap.recycle() }
    }

    /** Accepts actual PNG and JPEG files and recovers exact private bytes without a provider URI. */
    @Test fun realOriginalsDecodeAndSurviveStoreRecreation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.noBackupFilesDir, "photo-device-test-${java.util.UUID.randomUUID()}")
        try {
            for ((compression, format) in listOf(Bitmap.CompressFormat.PNG to OriginalPhotoFormat.PNG,
                Bitmap.CompressFormat.JPEG to OriginalPhotoFormat.JPEG)) {
                val bytes = image(compression)
                val store = OriginalPhotoStore(root, AndroidOriginalImageProbe())
                val photo = store.retain(fixtureSession("fixture-user"), "fixture-user", "capsule", format.mimeType, bytes.inputStream())
                val recovered = OriginalPhotoStore(root, AndroidOriginalImageProbe()).verify(photo)
                assertArrayEquals(bytes, recovered.readBytes())
                assertEquals(format, photo.format)
            }
        } finally { root.deleteRecursively() }
    }

    /** The decoder rejects a real PNG falsely declared as JPEG. */
    @Test fun realMimeMismatchIsRejected() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.noBackupFilesDir, "photo-device-test-${java.util.UUID.randomUUID()}")
        try {
            OriginalPhotoStore(root, AndroidOriginalImageProbe()).retain(fixtureSession("fixture-user"), "fixture-user", "capsule",
                "image/jpeg", image(Bitmap.CompressFormat.PNG).inputStream())
            fail("Mismatched image should be rejected")
        } catch (_: InvalidOriginalPhotoException) {
            // Expected: extension/provider MIME cannot substitute for actual decoding.
        } finally { root.deleteRecursively() }
    }

    /** Corrupt non-image bytes cannot pass merely because the declared MIME is supported. */
    @Test fun corruptImageIsRejected() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.noBackupFilesDir, "photo-device-test-${java.util.UUID.randomUUID()}")
        try {
            OriginalPhotoStore(root, AndroidOriginalImageProbe()).retain(fixtureSession("fixture-user"), "fixture-user", "capsule",
                "image/png", byteArrayOf(1, 2, 3).inputStream())
            fail("Corrupt image should be rejected")
        } catch (_: InvalidOriginalPhotoException) {
            // Expected: bounds/preview decoding failed.
        } finally { root.deleteRecursively() }
    }
}
