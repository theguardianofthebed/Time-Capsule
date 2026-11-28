package com.example.timecapsule.ui.s06

import com.example.timecapsule.domain.model.LocationTarget
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.ui.draft.CapsuleDraftUiState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** Assert the text passed to the actual review label, including incomplete recovered drafts. */
class S06ConfirmSendLockDisplayTest {
    private val draft = CapsuleDraftUiState(
        unlockAt = Instant.parse("2026-12-01T10:00:00Z"), creationZone = "Asia/Seoul",
        locationName = "Garden", locationTarget = LocationTarget(37.5, 127.0)
    )
    private val time = "2026-12-01 19:00 (Asia/Seoul, UTC+09:00)"

    /** Stale target fields do not appear on time-only drafts. */
    @Test fun timeOnlyUsesCreationZone() =
        assertEquals("Meant to open: $time", reviewCondition(draft))

    /** Location mode ignores a stale time. */
    @Test fun locationOnlyUsesValidTarget() =
        assertEquals("Meant to open: At Garden (150 m radius)", reviewCondition(draft.copy(lockMode = LockMode.LOCATION)))

    /** Both conditions must be satisfied at verification. */
    @Test fun bothExplicitlyRequiresTimeAndPlace() =
        assertEquals("Meant to open: $time AND at Garden (150 m radius)", reviewCondition(draft.copy(lockMode = LockMode.TIME_AND_LOCATION)))

    /** Missing evidence cannot establish a future time or a selected location. */
    @Test fun missingConditions() =
        assertEquals("Unlock time unavailable AND location unavailable", reviewCondition(
            draft.copy(lockMode = LockMode.TIME_AND_LOCATION, unlockAt = null, locationTarget = null)))

    /** A stale name cannot make an invalid coordinate valid. */
    @Test fun invalidCoordinates() =
        assertEquals("Location unavailable", reviewCondition(draft.copy(lockMode = LockMode.LOCATION,
            locationTarget = LocationTarget(Double.NaN, 127.0))))

    /** An unknown creation zone must be identified, independent of the reviewing device. */
    @Test fun unknownZoneUsesExplicitUtc() =
        assertEquals("Meant to open: 2026-12-01 10:00 (UTC, UTC+00:00) · Creation timezone unavailable",
            reviewCondition(draft.copy(creationZone = "broken")))
}
