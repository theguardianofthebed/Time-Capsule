package com.example.timecapsule.domain.location

import com.example.timecapsule.domain.model.ForegroundLocationSample
import com.example.timecapsule.domain.model.LocationQualificationResult
import com.example.timecapsule.domain.model.LocationTarget
import com.example.timecapsule.domain.model.QualificationFailureReason
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Interface boundary for computing WGS84 geographic distance between coordinates in meters.
 * Decouples domain qualification logic from Android runtime dependencies.
 */
interface DistanceCalculator {
    /** Returns WGS84 distance in meters between (startLat, startLng) and (endLat, endLng). */
    fun distanceBetweenMeters(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double
    ): Double
}

/**
 * Pure JVM Haversine formula implementation for WGS84 distance calculation.
 */
object HaversineDistanceCalculator : DistanceCalculator {
    private const val EARTH_RADIUS_METERS = 6371000.0

    override fun distanceBetweenMeters(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double
    ): Double {
        val dLat = Math.toRadians(endLat - startLat)
        val dLng = Math.toRadians(endLng - startLng)
        val a = (sin(dLat / 2) * sin(dLat / 2)) +
            (cos(Math.toRadians(startLat)) * cos(Math.toRadians(endLat)) *
            sin(dLng / 2) * sin(dLng / 2))
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }
}

/**
 * Pure deterministic domain location qualifier.
 * Evaluates whether a fresh [ForegroundLocationSample] satisfies a target [LocationTarget]
 * based on distance <= 150m, sample age in 0..60s, and accuracy <= 100m.
 */
object LocationQualifier {

    /** Maximum allowed sample age in nanoseconds (60 seconds). */
    const val MAX_AGE_NANOS: Long = 60_000_000_000L // 60 seconds

    /** Maximum allowed horizontal accuracy radius in meters. */
    const val MAX_ACCURACY_METERS: Float = 100.0f // 100 meters

    /** Default target radius threshold in meters if not specified by target. */
    const val DEFAULT_RADIUS_METERS: Double = 150.0 // 150 meters

    /**
     * Evaluates a location sample against a target location and monotonic timestamp.
     * Requires all three conditions to pass simultaneously:
     * 1. Distance <= target.radiusMeters (default 150m)
     * 2. Sample age = (nowElapsedRealtimeNanos - sample.elapsedRealtimeNanos) in 0..60s inclusive
     * 3. Reported horizontal accuracy <= 100m (must be non-null and finite)
     *
     * @param target The confirmed target location coordinates and radius.
     * @param sample The fresh foreground location fix acquired from device.
     * @param nowElapsedRealtimeNanos Monotonic timestamp from SystemClock.elapsedRealtimeNanos().
     * @param distanceCalculator Seam for distance calculation, defaulting to [HaversineDistanceCalculator].
     * @return Deterministic [LocationQualificationResult] detailing qualification status and reason.
     */
    fun qualify(
        target: LocationTarget?,
        sample: ForegroundLocationSample?,
        nowElapsedRealtimeNanos: Long,
        distanceCalculator: DistanceCalculator = HaversineDistanceCalculator
    ): LocationQualificationResult {

        if (target == null) {
            return LocationQualificationResult(
                isQualifying = false,
                reason = QualificationFailureReason.INVALID_TARGET
            )
        }

        if (!isValidCoordinate(target.latitude, target.longitude) || !target.radiusMeters.isFinite() || target.radiusMeters <= 0) {
            return LocationQualificationResult(
                isQualifying = false,
                reason = QualificationFailureReason.INVALID_TARGET
            )
        }

        if (sample == null) {
            return LocationQualificationResult(
                isQualifying = false,
                reason = QualificationFailureReason.MISSING_SAMPLE
            )
        }

        if (!isValidCoordinate(sample.latitude, sample.longitude)) {
            return LocationQualificationResult(
                isQualifying = false,
                reason = QualificationFailureReason.INVALID_COORDINATES
            )
        }

        // Monotonic age check relative to nowElapsedRealtimeNanos
        val ageNanos = nowElapsedRealtimeNanos - sample.elapsedRealtimeNanos

        if (ageNanos < 0L) {
            return LocationQualificationResult(
                isQualifying = false,
                reason = QualificationFailureReason.FUTURE_TIMESTAMP,
                sampleAgeNanos = ageNanos
            )
        }

        if (ageNanos > MAX_AGE_NANOS) {
            return LocationQualificationResult(
                isQualifying = false,
                reason = QualificationFailureReason.STALE_SAMPLE,
                sampleAgeNanos = ageNanos
            )
        }

        // Accuracy check: must be present, finite, non-negative, and <= 100m
        val accuracy = sample.horizontalAccuracyMeters
        if (accuracy == null || !accuracy.isFinite() || accuracy < 0f || accuracy > MAX_ACCURACY_METERS) {
            return LocationQualificationResult(
                isQualifying = false,
                reason = QualificationFailureReason.POOR_ACCURACY,
                sampleAgeNanos = ageNanos
            )
        }

        // WGS84 distance check
        val distance = distanceCalculator.distanceBetweenMeters(
            startLat = target.latitude,
            startLng = target.longitude,
            endLat = sample.latitude,
            endLng = sample.longitude
        )

        if (!distance.isFinite() || distance < 0) {
            return LocationQualificationResult(
                isQualifying = false,
                reason = QualificationFailureReason.INVALID_COORDINATES,
                distanceMeters = distance,
                sampleAgeNanos = ageNanos
            )
        }

        val allowedRadius = if (target.radiusMeters.isFinite() && target.radiusMeters > 0) {
            target.radiusMeters
        } else {
            DEFAULT_RADIUS_METERS
        }

        if (distance > allowedRadius) {
            return LocationQualificationResult(
                isQualifying = false,
                reason = QualificationFailureReason.OUTSIDE_RADIUS,
                distanceMeters = distance,
                sampleAgeNanos = ageNanos
            )
        }

        return LocationQualificationResult(
            isQualifying = true,
            reason = QualificationFailureReason.NONE,
            distanceMeters = distance,
            sampleAgeNanos = ageNanos
        )
    }

    /** Helper checking if latitude in [-90, 90] and longitude in [-180, 180] and both finite. */
    private fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
        return latitude.isFinite() && latitude in -90.0..90.0 &&
            longitude.isFinite() && longitude in -180.0..180.0
    }
}
