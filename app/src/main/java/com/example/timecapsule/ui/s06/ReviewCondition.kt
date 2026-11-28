package com.example.timecapsule.ui.s06

import com.example.timecapsule.domain.model.LocationTarget
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.domain.time.TimeConversionHelper
import com.example.timecapsule.ui.draft.CapsuleDraftUiState
import java.time.DateTimeException
import java.time.ZoneId

/** Format only the conditions represented by this draft. Missing fields stay visible as missing. */
internal fun reviewCondition(state: CapsuleDraftUiState): String {
    val time = state.unlockAt?.let { instant ->
        try {
            TimeConversionHelper.formatZonedDateTime(instant, ZoneId.of(state.creationZone))
        } catch (_: DateTimeException) {
            TimeConversionHelper.formatZonedDateTime(instant, ZoneId.of("UTC")) + " · Creation timezone unavailable"
        }
    }
    val place = state.locationTarget?.takeIf {
        it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 &&
            it.radiusMeters == LocationTarget.FIXED_RADIUS_METERS
    }?.let {
        val name = state.locationName?.takeIf(String::isNotBlank) ?: "${it.latitude}, ${it.longitude}"
        "$name (${it.radiusMeters.toInt()} m radius)"
    }
    return when (state.lockMode) {
        LockMode.TIME -> time?.let { "Meant to open: $it" } ?: "Unlock time unavailable"
        LockMode.LOCATION -> place?.let { "Meant to open: At $it" } ?: "Location unavailable"
        LockMode.TIME_AND_LOCATION -> {
            val timePart = time?.let { "Meant to open: $it" } ?: "Unlock time unavailable"
            val placePart = place?.let { "at $it" } ?: "location unavailable"
            "$timePart AND $placePart"
        }
    }
}
