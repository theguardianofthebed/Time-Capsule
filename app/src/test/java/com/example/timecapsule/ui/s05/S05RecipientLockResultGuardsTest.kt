package com.example.timecapsule.ui.s05

import com.example.timecapsule.domain.model.LocationTarget
import com.example.timecapsule.domain.session.SessionSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM unit tests exercising the host-side result guard logic for S-05 recipient lock.
 * Verifies strict rejection of obsolete, cross-account, cross-draft, or reauthenticated results.
 */
class S05RecipientLockResultGuardsTest {

    data class MapResultPayload(
        val capsuleId: String?,
        val sessionUid: String?,
        val sessionGen: Long?,
        val requestId: String?,
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Double
    )

    private fun isResultValidForHost(
        payload: MapResultPayload,
        editable: Boolean,
        activeCapsuleId: String,
        activeSession: SessionSnapshot,
        activeRequestId: String
    ): Boolean {
        if (!editable) return false

        if (payload.capsuleId.isNullOrBlank() || payload.capsuleId != activeCapsuleId) return false
        if (payload.sessionUid.isNullOrBlank() || payload.sessionUid != activeSession.uid) return false
        if (payload.sessionGen == null || payload.sessionGen != activeSession.sessionGeneration) return false
        if (payload.requestId.isNullOrBlank() || payload.requestId != activeRequestId) return false

        return payload.latitude.isFinite() && payload.latitude in -90.0..90.0 &&
            payload.longitude.isFinite() && payload.longitude in -180.0..180.0 &&
            payload.radiusMeters == LocationTarget.FIXED_RADIUS_METERS
    }

    private fun createPayload(
        capsuleId: String? = "capsule-1",
        sessionUid: String? = "user-a",
        sessionGen: Long? = 1L,
        requestId: String? = "req-123",
        lat: Double = 37.5665,
        lng: Double = 126.9780,
        radius: Double = 150.0
    ): MapResultPayload {
        return MapResultPayload(
            capsuleId = capsuleId,
            sessionUid = sessionUid,
            sessionGen = sessionGen,
            requestId = requestId,
            latitude = lat,
            longitude = lng,
            radiusMeters = radius
        )
    }

    @Test
    fun matchingResultIsAccepted() {
        val payload = createPayload()
        val isValid = isResultValidForHost(
            payload = payload,
            editable = true,
            activeCapsuleId = "capsule-1",
            activeSession = SessionSnapshot("user-a", 1L),
            activeRequestId = "req-123"
        )
        assertTrue(isValid)
    }

    @Test
    fun nonEditableStateRejectsResult() {
        val payload = createPayload()
        val isValid = isResultValidForHost(
            payload = payload,
            editable = false, // e.g. publication busy
            activeCapsuleId = "capsule-1",
            activeSession = SessionSnapshot("user-a", 1L),
            activeRequestId = "req-123"
        )
        assertFalse(isValid)
    }

    @Test
    fun wrongCapsuleIdRejectsResult() {
        val payload = createPayload(capsuleId = "old-capsule")
        val isValid = isResultValidForHost(
            payload = payload,
            editable = true,
            activeCapsuleId = "capsule-1",
            activeSession = SessionSnapshot("user-a", 1L),
            activeRequestId = "req-123"
        )
        assertFalse(isValid)
    }

    @Test
    fun wrongSessionUidRejectsResult() {
        val payload = createPayload(sessionUid = "user-b")
        val isValid = isResultValidForHost(
            payload = payload,
            editable = true,
            activeCapsuleId = "capsule-1",
            activeSession = SessionSnapshot("user-a", 1L),
            activeRequestId = "req-123"
        )
        assertFalse(isValid)
    }

    @Test
    fun sameUidNewSessionGenerationRejectsResult() {
        val payload = createPayload(sessionGen = 1L)
        val isValid = isResultValidForHost(
            payload = payload,
            editable = true,
            activeCapsuleId = "capsule-1",
            activeSession = SessionSnapshot("user-a", 2L), // Re-authenticated
            activeRequestId = "req-123"
        )
        assertFalse(isValid)
    }

    @Test
    fun obsoleteRequestIdRejectsResult() {
        val payload = createPayload(requestId = "old-req-000")
        val isValid = isResultValidForHost(
            payload = payload,
            editable = true,
            activeCapsuleId = "capsule-1",
            activeSession = SessionSnapshot("user-a", 1L),
            activeRequestId = "req-123"
        )
        assertFalse(isValid)
    }

    @Test
    fun invalidCoordinatesOrRadiusRejectsResult() {
        val badCoordPayload = createPayload(lat = 999.0)
        assertFalse(isResultValidForHost(badCoordPayload, true, "capsule-1", SessionSnapshot("user-a", 1L), "req-123"))

        val badRadiusPayload = createPayload(radius = 500.0)
        assertFalse(isResultValidForHost(badRadiusPayload, true, "capsule-1", SessionSnapshot("user-a", 1L), "req-123"))
    }
}
