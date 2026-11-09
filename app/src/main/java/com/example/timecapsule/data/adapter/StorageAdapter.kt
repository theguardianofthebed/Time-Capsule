package com.example.timecapsule.data.adapter

/**
 * Replaceable adapter boundary for Cloud Storage media operations.
 */
interface StorageAdapter {
    /** True if Cloud Storage adapter is configured and ready. */
    fun isAvailable(): Boolean
}
