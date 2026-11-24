package com.example.timecapsule.domain.model

/**
 * Android-free foreground device location sample container.
 * Captures numeric WGS84 coordinates, horizontal accuracy, and monotonic elapsed realtime timestamp.
 *
 * @property latitude Finite WGS84 latitude in [-90.0, 90.0].
 * @property longitude Finite WGS84 longitude in [-180.0, 180.0].
 * @property elapsedRealtimeNanos Monotonic timestamp from SystemClock.elapsedRealtimeNanos().
 * @property horizontalAccuracyMeters Reported horizontal accuracy radius in meters, or null if missing.
 */
data class ForegroundLocationSample(
    val latitude: Double,
    val longitude: Double,
    val elapsedRealtimeNanos: Long,
    val horizontalAccuracyMeters: Float? = null
)

/**
 * Status outcomes when querying device location via location adapter.
 */
enum class LocationSampleStatus {
    /** Fresh location fix acquired successfully. */
    SUCCESS,
    /** Precise ACCESS_FINE_LOCATION permission was denied. */
    PERMISSION_DENIED,
    /** System location services / GPS are disabled. */
    SERVICE_DISABLED,
    /** Google Play services or location provider unavailable. */
    SERVICE_UNAVAILABLE,
    /** Location query timed out or returned a null fix. */
    TIMEOUT_OR_NULL,
    /** Security exception occurred when checking permissions. */
    SECURITY_EXCEPTION
}

/**
 * Container result wrapper for location acquisition attempts.
 *
 * @property status Categorized status outcome of the request.
 * @property sample The acquired location fix, or null if acquisition failed.
 * @property errorMessage Human-readable message explaining failure details.
 */
data class LocationSampleResult(
    val status: LocationSampleStatus,
    val sample: ForegroundLocationSample? = null,
    val errorMessage: String? = null
)

/**
 * Categorized reasons for target location qualification failure.
 */
enum class QualificationFailureReason {
    /** Sample satisfies all distance, age, and accuracy criteria. */
    NONE,
    /** Sample is outside the required target radius (> 150m). */
    OUTSIDE_RADIUS,
    /** Sample age exceeds the maximum freshness threshold (> 60s). */
    STALE_SAMPLE,
    /** Sample timestamp is in the future relative to monotonic clock (age < 0). */
    FUTURE_TIMESTAMP,
    /** Reported horizontal accuracy exceeds 100m or is missing. */
    POOR_ACCURACY,
    /** Sample coordinates contain NaN, infinity, or are out of valid range. */
    INVALID_COORDINATES,
    /** Target location is null or contains invalid coordinate values. */
    INVALID_TARGET,
    /** Foreground location sample is null. */
    MISSING_SAMPLE
}

/**
 * Deterministic outcome result of evaluating a location fix against a target.
 *
 * @property isQualifying True only if distance <= 150m, age in 0..60s, and accuracy <= 100m.
 * @property reason Categorized failure reason, or [QualificationFailureReason.NONE] on success.
 * @property distanceMeters Computed WGS84 distance in meters between sample and target, or null if invalid.
 * @property sampleAgeNanos Computed sample age in nanoseconds relative to current monotonic time.
 */
data class LocationQualificationResult(
    val isQualifying: Boolean,
    val reason: QualificationFailureReason,
    val distanceMeters: Double? = null,
    val sampleAgeNanos: Long? = null
)
