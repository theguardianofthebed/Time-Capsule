package com.example.timecapsule.data.media

import android.graphics.BitmapFactory
import com.example.timecapsule.domain.model.OriginalPhotoFormat
import java.io.File

/** Checks decoded MIME/dimensions and decodes a sampled preview instead of the full image. */
class AndroidOriginalImageProbe : OriginalImageProbe {
    /** Returns the actual JPEG/PNG format only when a bounded preview can be decoded. */
    override fun inspect(file: File): OriginalPhotoFormat? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val format = OriginalPhotoFormat.entries.firstOrNull { it.mimeType == bounds.outMimeType }
            ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        // Power-of-two sampling bounds memory even for highly compressed large dimensions.
        while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val preview = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        preview.recycle()
        return format
    }
}
