package com.example.timecapsule.domain.condition

import com.example.timecapsule.domain.model.ForegroundLocationSample
import com.example.timecapsule.domain.model.LocationTarget
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.domain.model.QualificationFailureReason
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM Unit tests verifying [ConditionEvaluator] behavior for pure condition evaluation,
 * truth table simulation for TIME, LOCATION, and BOTH modes (AC-06 / T-19), and boundary conditions (T-42).
 */
class ConditionEvaluatorTest {

    private val targetSeoul = LocationTarget(latitude = 37.5665, longitude = 126.9780, radiusMeters = 150.0)

    @Test
    fun `evaluate TIME mode returns eligible without requiring location`() {
        val eval = ConditionEvaluator.evaluate(
            mode = LockMode.TIME,
            target = null,
            sample = null,
            nowElapsedRealtimeNanos = 100_000_000_000L
        )

        assertTrue(eval.isEligible)
        assertFalse(eval.isLocationRequired)
        assertNull(eval.locationResult)
        assertEquals(QualificationFailureReason.NONE, eval.failureReason)
    }

    @Test
    fun `evaluate LOCATION mode succeeds when sample is within 150m, fresh, and accurate`() {
        val sample = ForegroundLocationSample(
            latitude = 37.5665,
            longitude = 126.9780,
            elapsedRealtimeNanos = 90_000_000_000L, // 10s old relative to now=100s
            horizontalAccuracyMeters = 15.0f
        )

        val eval = ConditionEvaluator.evaluate(
            mode = LockMode.LOCATION,
            target = targetSeoul,
            sample = sample,
            nowElapsedRealtimeNanos = 100_000_000_000L
        )

        assertTrue(eval.isEligible)
        assertTrue(eval.isLocationRequired)
        assertEquals(QualificationFailureReason.NONE, eval.failureReason)
    }

    @Test
    fun `evaluate LOCATION mode fails when sample is outside 150m radius`() {
        // ~300m away
        val sample = ForegroundLocationSample(
            latitude = 37.5692,
            longitude = 126.9780,
            elapsedRealtimeNanos = 90_000_000_000L,
            horizontalAccuracyMeters = 10.0f
        )

        val eval = ConditionEvaluator.evaluate(
            mode = LockMode.LOCATION,
            target = targetSeoul,
            sample = sample,
            nowElapsedRealtimeNanos = 100_000_000_000L
        )

        assertFalse(eval.isEligible)
        assertEquals(QualificationFailureReason.OUTSIDE_RADIUS, eval.failureReason)
    }

    @Test
    fun `evaluate LOCATION mode fails when sample is older than 60s`() {
        val sample = ForegroundLocationSample(
            latitude = 37.5665,
            longitude = 126.9780,
            elapsedRealtimeNanos = 39_000_000_000L, // 61s old
            horizontalAccuracyMeters = 15.0f
        )

        val eval = ConditionEvaluator.evaluate(
            mode = LockMode.LOCATION,
            target = targetSeoul,
            sample = sample,
            nowElapsedRealtimeNanos = 100_000_000_000L
        )

        assertFalse(eval.isEligible)
        assertEquals(QualificationFailureReason.STALE_SAMPLE, eval.failureReason)
    }

    @Test
    fun `evaluate LOCATION mode fails when sample accuracy exceeds 100m`() {
        val sample = ForegroundLocationSample(
            latitude = 37.5665,
            longitude = 126.9780,
            elapsedRealtimeNanos = 90_000_000_000L,
            horizontalAccuracyMeters = 105.0f // > 100m
        )

        val eval = ConditionEvaluator.evaluate(
            mode = LockMode.LOCATION,
            target = targetSeoul,
            sample = sample,
            nowElapsedRealtimeNanos = 100_000_000_000L
        )

        assertFalse(eval.isEligible)
        assertEquals(QualificationFailureReason.POOR_ACCURACY, eval.failureReason)
    }

    @Test
    fun `evaluate BOTH mode tests all four truth combinations (T-19)`() {
        val unlockAt = Instant.parse("2026-11-25T12:00:00Z")
        val earlyTime = Instant.parse("2026-11-25T11:59:59Z")
        val validTime = Instant.parse("2026-11-25T12:00:00Z")

        val sampleInside = ForegroundLocationSample(
            latitude = 37.5665,
            longitude = 126.9780,
            elapsedRealtimeNanos = 90_000_000_000L,
            horizontalAccuracyMeters = 20.0f
        )
        val sampleOutside = ForegroundLocationSample(
            latitude = 37.5692,
            longitude = 126.9780,
            elapsedRealtimeNanos = 90_000_000_000L,
            horizontalAccuracyMeters = 20.0f
        )

        // 1. F / F: Time early, location outside => LOCKED
        val evalFF = ConditionEvaluator.evaluate(
            mode = LockMode.TIME_AND_LOCATION,
            target = targetSeoul,
            sample = sampleOutside,
            nowElapsedRealtimeNanos = 100_000_000_000L,
            simulatedServerTime = earlyTime,
            unlockAt = unlockAt
        )
        assertFalse(evalFF.isEligible)

        // 2. F / T: Time early, location inside => LOCKED
        val evalFT = ConditionEvaluator.evaluate(
            mode = LockMode.TIME_AND_LOCATION,
            target = targetSeoul,
            sample = sampleInside,
            nowElapsedRealtimeNanos = 100_000_000_000L,
            simulatedServerTime = earlyTime,
            unlockAt = unlockAt
        )
        assertFalse(evalFT.isEligible)

        // 3. T / F: Time reached, location outside => LOCKED
        val evalTF = ConditionEvaluator.evaluate(
            mode = LockMode.TIME_AND_LOCATION,
            target = targetSeoul,
            sample = sampleOutside,
            nowElapsedRealtimeNanos = 100_000_000_000L,
            simulatedServerTime = validTime,
            unlockAt = unlockAt
        )
        assertFalse(evalTF.isEligible)

        // 4. T / T: Time reached, location inside => AVAILABLE (Qualifies)
        val evalTT = ConditionEvaluator.evaluate(
            mode = LockMode.TIME_AND_LOCATION,
            target = targetSeoul,
            sample = sampleInside,
            nowElapsedRealtimeNanos = 100_000_000_000L,
            simulatedServerTime = validTime,
            unlockAt = unlockAt
        )
        assertTrue(evalTT.isEligible)
    }

    @Test
    fun `evaluate boundary limits for age 60s and accuracy 100m (T-42)`() {
        val sampleBoundary = ForegroundLocationSample(
            latitude = 37.5665,
            longitude = 126.9780,
            elapsedRealtimeNanos = 40_000_000_000L, // exactly 60s old relative to 100s
            horizontalAccuracyMeters = 100.0f // exactly 100m
        )

        val evalPass = ConditionEvaluator.evaluate(
            mode = LockMode.LOCATION,
            target = targetSeoul,
            sample = sampleBoundary,
            nowElapsedRealtimeNanos = 100_000_000_000L
        )
        assertTrue(evalPass.isEligible)

        val sampleAgeFail = sampleBoundary.copy(elapsedRealtimeNanos = 39_999_999_999L) // 60.000000001s old
        val evalFailAge = ConditionEvaluator.evaluate(
            mode = LockMode.LOCATION,
            target = targetSeoul,
            sample = sampleAgeFail,
            nowElapsedRealtimeNanos = 100_000_000_000L
        )
        assertFalse(evalFailAge.isEligible)
        assertEquals(QualificationFailureReason.STALE_SAMPLE, evalFailAge.failureReason)

        val sampleAccFail = sampleBoundary.copy(horizontalAccuracyMeters = 100.1f)
        val evalFailAcc = ConditionEvaluator.evaluate(
            mode = LockMode.LOCATION,
            target = targetSeoul,
            sample = sampleAccFail,
            nowElapsedRealtimeNanos = 100_000_000_000L
        )
        assertFalse(evalFailAcc.isEligible)
        assertEquals(QualificationFailureReason.POOR_ACCURACY, evalFailAcc.failureReason)
    }
}
