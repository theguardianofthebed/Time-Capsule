package com.example.timecapsule.data.adapter

/**
 * Replaceable adapter boundary for current location queries.
 */
interface LocationAdapter {
    /** True if location services are enabled and permissions granted. */
    fun isLocationAvailable(): Boolean
}
