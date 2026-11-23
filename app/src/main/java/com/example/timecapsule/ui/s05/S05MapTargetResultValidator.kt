package com.example.timecapsule.ui.s05

import android.os.Bundle
import com.example.timecapsule.domain.model.LocationTarget
import com.example.timecapsule.domain.session.SessionSnapshot

/**
 * Pure validator evaluating FragmentResult bundles received by S05RecipientLockFragment from S05MapTargetDialogFragment.
 * Enforces strict non-null matching for capsule ID, session UID, session generation, one-time request ID,
 * editable draft state, WGS84 coordinate bounds, and fixed 150m radius.
 */
object S05MapTargetResultValidator {

    /**
     * Pure container for result validation parameters extracted from host UI state and session tracker.
     */
    data class ValidationContext(
        val editable: Boolean,
        val activeCapsuleId: String,
        val activeSession: SessionSnapshot,
        val activeRequestId: String?
    )

    /**
     * Pure container for map selection payload values extracted from result bundle or DTO.
     */
    data class ResultPayload(
        val capsuleId: String?,
        val sessionUid: String?,
        val sessionGeneration: Long?,
        val requestId: String?,
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Double,
        val displayName: String?
    ) {
        companion object {
            /** Extracts typed [ResultPayload] from an Android [Bundle]. */
            fun fromBundle(bundle: Bundle): ResultPayload {
                val capsuleId = bundle.getString(S05MapTargetDialogFragment.RESULT_KEY_CAPSULE_ID)
                val sessionUid = bundle.getString(S05MapTargetDialogFragment.RESULT_KEY_SESSION_UID)
                val sessionGen = if (bundle.containsKey(S05MapTargetDialogFragment.RESULT_KEY_SESSION_GENERATION)) {
                    bundle.getLong(S05MapTargetDialogFragment.RESULT_KEY_SESSION_GENERATION)
                } else null
                val requestId = bundle.getString(S05MapTargetDialogFragment.RESULT_KEY_REQUEST_ID)
                val lat = bundle.getDouble(S05MapTargetDialogFragment.RESULT_KEY_LATITUDE, Double.NaN)
                val lng = bundle.getDouble(S05MapTargetDialogFragment.RESULT_KEY_LONGITUDE, Double.NaN)
                val radius = bundle.getDouble(S05MapTargetDialogFragment.RESULT_KEY_RADIUS, Double.NaN)
                val displayName = bundle.getString(S05MapTargetDialogFragment.RESULT_KEY_DISPLAY_NAME)

                return ResultPayload(
                    capsuleId = capsuleId,
                    sessionUid = sessionUid,
                    sessionGeneration = sessionGen,
                    requestId = requestId,
                    latitude = lat,
                    longitude = lng,
                    radiusMeters = radius,
                    displayName = displayName
                )
            }
        }
    }

    /**
     * Evaluates whether a result payload is valid for the current host context.
     * Requires:
     * 1. Host draft is editable.
     * 2. Non-blank active request ID.
     * 3. Non-blank matching capsule ID.
     * 4. Non-blank matching session UID.
     * 5. Non-null matching session generation.
     * 6. Non-blank matching one-time request ID.
     * 7. Finite WGS84 latitude in [-90, 90] and longitude in [-180, 180].
     * 8. Radius equals fixed 150.0m.
     */
    fun validateResult(payload: ResultPayload, context: ValidationContext): Boolean {
        if (!context.editable) return false
        if (context.activeRequestId.isNullOrBlank()) return false

        if (payload.capsuleId.isNullOrBlank() || payload.capsuleId != context.activeCapsuleId) return false
        if (payload.sessionUid.isNullOrBlank() || payload.sessionUid != context.activeSession.uid) return false
        if (payload.sessionGeneration == null || payload.sessionGeneration != context.activeSession.sessionGeneration) return false
        if (payload.requestId.isNullOrBlank() || payload.requestId != context.activeRequestId) return false

        return payload.latitude.isFinite() && payload.latitude in -90.0..90.0 &&
            payload.longitude.isFinite() && payload.longitude in -180.0..180.0 &&
            payload.radiusMeters == LocationTarget.FIXED_RADIUS_METERS
    }
}
