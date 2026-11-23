package com.example.timecapsule.domain.location

import com.example.timecapsule.domain.model.ForegroundLocationSample
import com.example.timecapsule.domain.model.LocationTarget
import com.example.timecapsule.domain.model.QualificationFailureReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic JVM unit tests for [LocationQualifier] exercising T-16, T-18, and T-42 boundary rules:
 * - Distance <= 150m
 * - Monotonic sample age in 0..60s
 * - Horizontal accuracy <= 100m
 * - Non-latching prior truth on subsequent checks
 */
class LocationQualifierTest {

    private val targetLat = 37.5665
    private val targetLng = 126.9780
    private val target = LocationTarget(latitude = targetLat, longitude = targetLng, radiusMeters = 150.0)

    private val baseNowNanos: Long = 100_000_000_000L

    @Test
    fun validSampleWithin150m60s100mPasses() {
        val sample = ForegroundLocationSample(
            latitude = targetLat,
            longitude = targetLng,
            elapsedRealtimeNanos = baseNowNanos - 10_000_000_000L, // 10s age
            horizontalAccuracyMeters = 15f
        )

        val result = LocationQualifier.qualify(target, sample, baseNowNanos)

        assertTrue(result.isQualifying)
        assertEquals(QualificationFailureReason.NONE, result.reason)
        assertEquals(0.0, result.distanceMeters!!, 0.01)
        assertEquals(10_000_000_000L, result.sampleAgeNanos)
    }

    @Test
    fun distanceBoundariesTestedStrictly() {
        // Mock distance calculator to simulate exact distance returns
        val exactDistanceCalculator = object : DistanceCalculator {
            var dist: Double = 0.0
            override fun distanceBetweenMeters(
                startLat: Double,
                startLng: Double,
                endLat: Double,
                endLng: Double
            ): Double = dist
        }

        val sample = ForegroundLocationSample(
            latitude = targetLat,
            longitude = targetLng,
            elapsedRealtimeNanos = baseNowNanos - 5_000_000_000L,
            horizontalAccuracyMeters = 20f
        )

        // Exactly 150.0m passes
        exactDistanceCalculator.dist = 150.0
        val passResult = LocationQualifier.qualify(target, sample, baseNowNanos, exactDistanceCalculator)
        assertTrue(passResult.isQualifying)

        // 150.1m fails
        exactDistanceCalculator.dist = 150.1
        val failResult = LocationQualifier.qualify(target, sample, baseNowNanos, exactDistanceCalculator)
        assertFalse(failResult.isQualifying)
        assertEquals(QualificationFailureReason.OUTSIDE_RADIUS, failResult.reason)
    }

    @Test
    fun ageBoundariesTestedStrictly() {
        val sampleAt60s = ForegroundLocationSample(
            latitude = targetLat,
            longitude = targetLng,
            elapsedRealtimeNanos = baseNowNanos - 60_000_000_000L, // exactly 60s
            horizontalAccuracyMeters = 10f
        )
        val result60s = LocationQualifier.qualify(target, sampleAt60s, baseNowNanos)
        assertTrue(result60s.isQualifying)

        val sampleAt60_1s = ForegroundLocationSample(
            latitude = targetLat,
            longitude = targetLng,
            elapsedRealtimeNanos = baseNowNanos - 60_100_000_000L, // 60.1s
            horizontalAccuracyMeters = 10f
        )
        val resultStale = LocationQualifier.qualify(target, sampleAt60_1s, baseNowNanos)
        assertFalse(resultStale.isQualifying)
        assertEquals(QualificationFailureReason.STALE_SAMPLE, resultStale.reason)

        val sampleFuture = ForegroundLocationSample(
            latitude = targetLat,
            longitude = targetLng,
            elapsedRealtimeNanos = baseNowNanos + 1_000_000_000L, // future timestamp
            horizontalAccuracyMeters = 10f
        )
        val resultFuture = LocationQualifier.qualify(target, sampleFuture, baseNowNanos)
        assertFalse(resultFuture.isQualifying)
        assertEquals(QualificationFailureReason.FUTURE_TIMESTAMP, resultFuture.reason)
    }

    @Test
    fun accuracyBoundariesTestedStrictly() {
        val sample100m = ForegroundLocationSample(
            latitude = targetLat,
            longitude = targetLng,
            elapsedRealtimeNanos = baseNowNanos - 10_000_000_000L,
            horizontalAccuracyMeters = 100.0f
        )
        val res100m = LocationQualifier.qualify(target, sample100m, baseNowNanos)
        assertTrue(res100m.isQualifying)

        val sample100_1m = ForegroundLocationSample(
            latitude = targetLat,
            longitude = targetLng,
            elapsedRealtimeNanos = baseNowNanos - 10_000_000_000L,
            horizontalAccuracyMeters = 100.1f
        )
        val resPoor = LocationQualifier.qualify(target, sample100_1m, baseNowNanos)
        assertFalse(resPoor.isQualifying)
        assertEquals(QualificationFailureReason.POOR_ACCURACY, resPoor.reason)

        val sampleNullAccuracy = ForegroundLocationSample(
            latitude = targetLat,
            longitude = targetLng,
            elapsedRealtimeNanos = baseNowNanos - 10_000_000_000L,
            horizontalAccuracyMeters = null
        )
        val resNull = LocationQualifier.qualify(target, sampleNullAccuracy, baseNowNanos)
        assertFalse(resNull.isQualifying)
        assertEquals(QualificationFailureReason.POOR_ACCURACY, resNull.reason)
    }

    @Test
    fun invalidCoordinatesAndNullParametersRejected() {
        val resNullTarget = LocationQualifier.qualify(null, ForegroundLocationSample(targetLat, targetLng, baseNowNanos, 10f), baseNowNanos)
        assertFalse(resNullTarget.isQualifying)
        assertEquals(QualificationFailureReason.INVALID_TARGET, resNullTarget.reason)

        val resNullSample = LocationQualifier.qualify(target, null, baseNowNanos)
        assertFalse(resNullSample.isQualifying)
        assertEquals(QualificationFailureReason.MISSING_SAMPLE, resNullSample.reason)

        val invalidSample = ForegroundLocationSample(
            latitude = 95.0, // out of range
            longitude = targetLng,
            elapsedRealtimeNanos = baseNowNanos - 10_000_000_000L,
            horizontalAccuracyMeters = 10f
        )
        val resInvalid = LocationQualifier.qualify(target, invalidSample, baseNowNanos)
        assertFalse(resInvalid.isQualifying)
        assertEquals(QualificationFailureReason.INVALID_COORDINATES, resInvalid.reason)
    }

    @Test
    fun qualificationDoesNotLatchPriorTruth() {
        val freshSample = ForegroundLocationSample(
            latitude = targetLat,
            longitude = targetLng,
            elapsedRealtimeNanos = baseNowNanos - 5_000_000_000L,
            horizontalAccuracyMeters = 10f
        )
        val initialRes = LocationQualifier.qualify(target, freshSample, baseNowNanos)
        assertTrue(initialRes.isQualifying)

        // Time advances 70 seconds later; re-evaluating the same sample without a new fix MUST fail
        val laterNowNanos = baseNowNanos + 70_000_000_000L
        val recheckRes = LocationQualifier.qualify(target, freshSample, laterNowNanos)
        assertFalse(recheckRes.isQualifying)
        assertEquals(QualificationFailureReason.STALE_SAMPLE, recheckRes.reason)
    }

    @Test
    fun validateSampleQualityRejectsInvalidOrStaleOrInaccurateFixes() {
        val validSample = ForegroundLocationSample(targetLat, targetLng, baseNowNanos - 5_000_000_000L, 10f)
        assertEquals(QualificationFailureReason.NONE, LocationQualifier.validateSampleQuality(validSample, baseNowNanos))

        val zeroTimestamp = ForegroundLocationSample(targetLat, targetLng, 0L, 10f)
        assertEquals(QualificationFailureReason.INVALID_COORDINATES, LocationQualifier.validateSampleQuality(zeroTimestamp, baseNowNanos))

        val staleSample = ForegroundLocationSample(targetLat, targetLng, baseNowNanos - 65_000_000_000L, 10f)
        assertEquals(QualificationFailureReason.STALE_SAMPLE, LocationQualifier.validateSampleQuality(staleSample, baseNowNanos))

        val poorAccSample = ForegroundLocationSample(targetLat, targetLng, baseNowNanos - 5_000_000_000L, 150f)
        assertEquals(QualificationFailureReason.POOR_ACCURACY, LocationQualifier.validateSampleQuality(poorAccSample, baseNowNanos))
    }
}
