package com.example.timecapsule.data.adapter

import com.example.timecapsule.domain.result.OperationResult

/**
 * Replaceable adapter boundary for Cloud Storage media operations.
 */
interface StorageAdapter {
    /** True if Cloud Storage adapter is configured and ready. */
    fun isAvailable(): Boolean

    /** Downloads raw photo bytes from Cloud Storage path [objectPath]. */
    suspend fun downloadPhotoBytes(objectPath: String, maxBytes: Long): OperationResult<ByteArray> {
        return OperationResult.Unavailable("Photo download is not implemented in this adapter.")
    }
}
