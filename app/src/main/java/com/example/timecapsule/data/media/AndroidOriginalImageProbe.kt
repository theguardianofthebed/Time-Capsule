package com.example.timecapsule.data.media

import android.graphics.BitmapFactory
import com.example.timecapsule.domain.model.OriginalPhotoFormat
import com.example.timecapsule.domain.model.OriginalPhotoLimits
import java.io.File

/** Uses the same bounded Android decode for selected, downloaded and retained originals. */
class AndroidOriginalImageProbe : OriginalImageProbe {
    override fun inspect(file: File): OriginalPhotoFormat? =
        if (OriginalPhotoLimits.acceptsSize(file.length())) inspect(file.readBytes()) else null

    companion object {
        private val pngEnd = byteArrayOf(0, 0, 0, 0, 73, 69, 78, 68, -82, 66, 96, -126)

        fun inspect(bytes: ByteArray): OriginalPhotoFormat? {
            if (!OriginalPhotoLimits.acceptsSize(bytes.size.toLong())) return null
            return try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val format = OriginalPhotoFormat.entries.find { it.mimeType == bounds.outMimeType }
                    ?: return null
                // Some decoders render a partial preview even when the download is truncated.
                val complete = when (format) {
                    OriginalPhotoFormat.JPEG -> bytes.size >= 2 &&
                        bytes[bytes.lastIndex - 1] == (-1).toByte() && bytes.last() == (-39).toByte()
                    OriginalPhotoFormat.PNG -> bytes.size >= pngEnd.size && pngEnd.indices.all {
                        bytes[bytes.size - pngEnd.size + it] == pngEnd[it]
                    }
                }
                if (!complete || bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 512) {
                        inSampleSize *= 2
                    }
                }
                val preview = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
                preview.recycle()
                format
            } catch (_: OutOfMemoryError) {
                null
            }
        }
    }
}
