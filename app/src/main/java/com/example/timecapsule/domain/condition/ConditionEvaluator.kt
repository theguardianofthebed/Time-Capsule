package com.example.timecapsule.domain.condition

import com.example.timecapsule.domain.location.DistanceCalculator
import com.example.timecapsule.domain.location.HaversineDistanceCalculator
import com.example.timecapsule.domain.location.LocationQualifier
import com.example.timecapsule.domain.model.ForegroundLocationSample
import com.example.timecapsule.domain.model.LocationQualificationResult
import com.example.timecapsule.domain.model.LocationTarget
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.domain.model.QualificationFailureReason
import java.time.Instant

/**
 * Categorized outcome of evaluating client-side lock conditions.
 *
 * @property isEligible True if client-side conditions (location requirement, sample quality, distance) pass.
 *                     Note: Does NOT claim server-time approval, which is strictly enforced by server security rules.
 * @property isLocationRequired True if the lock mode requires location qualification (LOCATION or TIME_AND_LOCATION).
 * @property locationResult The detailed qualification result from [LocationQualifier], or null if location was not required.
 * @property failureReason Categorized failure reason, or [QualificationFailureReason.NONE] if eligible.
 * @property simulatedTimeEligible Optional test-only evaluation of time condition when simulated server time is provided.
 */
data class ConditionEvaluationResult(
    val isEligible: Boolean,
    val isLocationRequired: Boolean,
    val locationResult: LocationQualificationResult? = null,
    val failureReason: QualificationFailureReason = QualificationFailureReason.NONE,
    val simulatedTimeEligible: Boolean? = null
)

/**
 * Pure stateless domain evaluator for capsule unlock conditions.
 * Reuses [LocationQualifier.qualify] for location checks without caching or latching past visits.
 *
 * Design and Security Rule Integrity (ARC-05, DD-05):
 * 1. Never remembers previous truth or persists `locationOK` / `timeOK` flags as future authorization.
 * 2. Evaluates current location sample age (0..60s), accuracy (<=100m), and distance (<=150m) dynamically.
 * 3. Never manufactures server time from device clock in production; server rules enforce `request.time >= unlockAt`.
 */
object ConditionEvaluator {

    /**
     * Evaluates client-side conditions for a given lock mode, target location, current sample, and monotonic timestamp.
     *
     * @param mode Capsule lock mode ([LockMode.TIME], [LockMode.LOCATION], or [LockMode.TIME_AND_LOCATION]).
     * @param target Persisted target location coordinates and 150m radius, or null if TIME mode.
     * @param sample Fresh foreground location fix acquired from device, or null.
     * @param nowElapsedRealtimeNanos Monotonic timestamp from SystemClock.elapsedRealtimeNanos().
     * @param distanceCalculator WGS84 distance calculator seam, defaulting to [HaversineDistanceCalculator].
     * @param simulatedServerTime Optional trusted server time instant used strictly for JVM/emulator test simulation.
     * @param unlockAt Optional scheduled unlock instant used strictly for JVM/emulator test simulation.
     * @return Deterministic [ConditionEvaluationResult] detailing eligibility and failure reasons.
     */
    fun evaluate(
        mode: LockMode,
        target: LocationTarget?,
        sample: ForegroundLocationSample?,
        nowElapsedRealtimeNanos: Long,
        distanceCalculator: DistanceCalculator = HaversineDistanceCalculator,
        simulatedServerTime: Instant? = null,
        unlockAt: Instant? = null
    ): ConditionEvaluationResult {
        // Optional test-only simulation of server time condition for truth-table verification
        val timeEligible = if (simulatedServerTime != null && unlockAt != null) {
            !simulatedServerTime.isBefore(unlockAt)
        } else {
            null
        }

        return when (mode) {
            LockMode.TIME -> {
                // For TIME mode, location fix is not required. Server rules validate server timestamp against unlockAt.
                val isEligible = timeEligible ?: true
                ConditionEvaluationResult(
                    isEligible = isEligible,
                    isLocationRequired = false,
                    locationResult = null,
                    failureReason = if (isEligible) QualificationFailureReason.NONE else QualificationFailureReason.STALE_SAMPLE,
                    simulatedTimeEligible = timeEligible
                )
            }
            LockMode.LOCATION -> {
                // For LOCATION mode, evaluate fresh foreground sample against target coordinates and radius.
                val locQual = LocationQualifier.qualify(target, sample, nowElapsedRealtimeNanos, distanceCalculator)
                ConditionEvaluationResult(
                    isEligible = locQual.isQualifying,
                    isLocationRequired = true,
                    locationResult = locQual,
                    failureReason = locQual.reason,
                    simulatedTimeEligible = null
                )
            }
            LockMode.TIME_AND_LOCATION -> {
                // For TIME_AND_LOCATION (BOTH) mode, both location qualification and server time must succeed.
                val locQual = LocationQualifier.qualify(target, sample, nowElapsedRealtimeNanos, distanceCalculator)
                val locationEligible = locQual.isQualifying
                val isEligible = if (timeEligible != null) {
                    locationEligible && timeEligible
                } else {
                    locationEligible
                }
                ConditionEvaluationResult(
                    isEligible = isEligible,
                    isLocationRequired = true,
                    locationResult = locQual,
                    failureReason = locQual.reason,
                    simulatedTimeEligible = timeEligible
                )
            }
        }
    }
}
