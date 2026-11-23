package com.example.timecapsule.ui.s05

import com.example.timecapsule.domain.session.SessionSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM unit tests exercising [S05MapTargetResultValidator] production guard logic.
 * Verifies strict rejection of obsolete, cross-account, cross-draft, or reauthenticated results.
 */
class S05RecipientLockResultGuardsTest {

    private fun createPayload(
        capsuleId: String? = "capsule-1",
        sessionUid: String? = "user-a",
        sessionGen: Long? = 1L,
        requestId: String? = "req-123",
        lat: Double = 37.5665,
        lng: Double = 126.9780,
        radius: Double = 150.0
    ): S05MapTargetResultValidator.ResultPayload {
        return S05MapTargetResultValidator.ResultPayload(
            capsuleId = capsuleId,
            sessionUid = sessionUid,
            sessionGeneration = sessionGen,
            requestId = requestId,
            latitude = lat,
            longitude = lng,
            radiusMeters = radius,
            displayName = "37.5665, 126.9780"
        )
    }

    private fun createContext(
        editable: Boolean = true,
        activeCapsuleId: String = "capsule-1",
        activeSession: SessionSnapshot = SessionSnapshot("user-a", 1L),
        activeRequestId: String? = "req-123"
    ): S05MapTargetResultValidator.ValidationContext {
        return S05MapTargetResultValidator.ValidationContext(
            editable = editable,
            activeCapsuleId = activeCapsuleId,
            activeSession = activeSession,
            activeRequestId = activeRequestId
        )
    }

    @Test
    fun matchingResultIsAccepted() {
        val payload = createPayload()
        val context = createContext()
        assertTrue(S05MapTargetResultValidator.validateResult(payload, context))
    }

    @Test
    fun nonEditableStateRejectsResult() {
        val payload = createPayload()
        val context = createContext(editable = false)
        assertFalse(S05MapTargetResultValidator.validateResult(payload, context))
    }

    @Test
    fun wrongCapsuleIdRejectsResult() {
        val payload = createPayload(capsuleId = "old-capsule")
        val context = createContext()
        assertFalse(S05MapTargetResultValidator.validateResult(payload, context))
    }

    @Test
    fun wrongSessionUidRejectsResult() {
        val payload = createPayload(sessionUid = "user-b")
        val context = createContext()
        assertFalse(S05MapTargetResultValidator.validateResult(payload, context))
    }

    @Test
    fun sameUidNewSessionGenerationRejectsResult() {
        val payload = createPayload(sessionGen = 1L)
        val context = createContext(activeSession = SessionSnapshot("user-a", 2L))
        assertFalse(S05MapTargetResultValidator.validateResult(payload, context))
    }

    @Test
    fun obsoleteRequestIdRejectsResult() {
        val payload = createPayload(requestId = "old-req-000")
        val context = createContext()
        assertFalse(S05MapTargetResultValidator.validateResult(payload, context))
    }

    @Test
    fun invalidCoordinatesOrRadiusRejectsResult() {
        val badCoordPayload = createPayload(lat = 999.0)
        assertFalse(S05MapTargetResultValidator.validateResult(badCoordPayload, createContext()))

        val badRadiusPayload = createPayload(radius = 500.0)
        assertFalse(S05MapTargetResultValidator.validateResult(badRadiusPayload, createContext()))
    }
}
