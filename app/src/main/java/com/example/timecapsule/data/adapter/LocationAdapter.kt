package com.example.timecapsule.data.adapter

import com.example.timecapsule.domain.model.LocationSampleResult

/**
 * Replaceable adapter boundary for current location queries and permission checks.
 */
interface LocationAdapter {
    /** True if location services are enabled and permissions granted. */
    fun isLocationAvailable(): Boolean

    /** True if precise ACCESS_FINE_LOCATION permission is granted by the system. */
    fun isFineLocationGranted(): Boolean

    /**
     * Obtains a fresh, one-shot foreground device location sample using FusedLocationProviderClient.
     * Returns a typed [LocationSampleResult] indicating success or specific failure reason.
     */
    suspend fun getCurrentLocation(): LocationSampleResult
}
