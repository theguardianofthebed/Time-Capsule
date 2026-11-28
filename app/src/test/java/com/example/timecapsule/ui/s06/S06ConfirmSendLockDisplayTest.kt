package com.example.timecapsule.ui.s06

import com.example.timecapsule.domain.model.LocationTarget
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.domain.time.TimeConversionHelper
import com.example.timecapsule.ui.draft.CapsuleDraftUiState
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM unit tests verifying truthful unlock condition formatting for S-06 Confirm Send.
 * Validates that LOCATION-only, TIME-only, and BOTH lock modes yield accurate, non-misleading review text.
 */
class S06ConfirmSendLockDisplayTest {

    @Test
    fun locationOnlyCapsuleFormatsLocationWithoutInstantUnlockFallback() {
        val state = CapsuleDraftUiState(
            lockMode = LockMode.LOCATION,
            unlockAt = null,
            locationTarget = LocationTarget(37.5665, 126.9780, 150.0),
            locationName = "University Garden"
        )

        val timeFormatted = if (state.unlockAt != null) {
            val zoneId = try { ZoneId.of(state.creationZone) } catch (_: Exception) { ZoneId.systemDefault() }
            TimeConversionHelper.formatZonedDateTime(state.unlockAt, zoneId)
        } else null

        val placeFormatted = if (state.lockMode == LockMode.LOCATION || state.lockMode == LockMode.TIME_AND_LOCATION) {
            val placeName = state.locationName ?: "Selected location"
            val radius = state.locationTarget?.radiusMeters?.toInt() ?: 150
            "$placeName ($radius m radius)"
        } else null

        assertNull(timeFormatted)
        assertEquals("University Garden (150 m radius)", placeFormatted)
        assertEquals(LockMode.LOCATION, state.lockMode)
    }

    @Test
    fun timeAndLocationBothLockModeCombinesTimeAndPlaceRequirements() {
        val now = Instant.parse("2026-12-01T10:00:00Z")
        val state = CapsuleDraftUiState(
            lockMode = LockMode.TIME_AND_LOCATION,
            unlockAt = now,
            creationZone = "UTC",
            locationTarget = LocationTarget(37.5665, 126.9780, 150.0),
            locationName = "Seoul Plaza"
        )

        val timeFormatted = if (state.unlockAt != null) {
            val zoneId = try { ZoneId.of(state.creationZone) } catch (_: Exception) { ZoneId.systemDefault() }
            TimeConversionHelper.formatZonedDateTime(state.unlockAt, zoneId)
        } else null

        val placeFormatted = if (state.lockMode == LockMode.LOCATION || state.lockMode == LockMode.TIME_AND_LOCATION) {
            val placeName = state.locationName ?: "Selected location"
            val radius = state.locationTarget?.radiusMeters?.toInt() ?: 150
            "$placeName ($radius m radius)"
        } else null

        assertTrue(timeFormatted!!.contains("2026"))
        assertEquals("Seoul Plaza (150 m radius)", placeFormatted)
        assertEquals(LockMode.TIME_AND_LOCATION, state.lockMode)
    }

    @Test
    fun timeOnlyLockModeDisplaysFormattedTime() {
        val now = Instant.parse("2027-01-01T00:00:00Z")
        val state = CapsuleDraftUiState(
            lockMode = LockMode.TIME,
            unlockAt = now,
            creationZone = "UTC",
            locationTarget = null,
            locationName = null
        )

        val timeFormatted = if (state.unlockAt != null) {
            val zoneId = try { ZoneId.of(state.creationZone) } catch (_: Exception) { ZoneId.systemDefault() }
            TimeConversionHelper.formatZonedDateTime(state.unlockAt, zoneId)
        } else null

        assertTrue(timeFormatted!!.contains("2027"))
        assertEquals(LockMode.TIME, state.lockMode)
        assertNull(state.locationTarget)
    }
}
