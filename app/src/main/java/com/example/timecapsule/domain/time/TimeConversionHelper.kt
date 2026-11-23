package com.example.timecapsule.domain.time

import com.example.timecapsule.domain.result.OperationResult
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Result data class representing a successful local date/time conversion to a UTC [Instant].
 *
 * @property instant The absolute UTC instant computed from the local date/time and timezone.
 * @property zoneId The IANA creation [ZoneId] used for the conversion.
 * @property offset The specific [ZoneOffset] applied (e.g. UTC+09:00 or UTC-05:00).
 * @property validOffsets The list of valid offsets for this local time (contains >1 offset during DST fall-back overlap).
 */
data class TimeConversionResult(
    val instant: Instant,
    val zoneId: ZoneId,
    val offset: ZoneOffset,
    val validOffsets: List<ZoneOffset>
) {
    /** True if this local date/time falls into an ambiguous Daylight Saving Time overlap. */
    val isAmbiguousOverlap: Boolean
        get() = validOffsets.size > 1
}

/**
 * Domain utility for converting local date and time inputs to absolute UTC [Instant]s
 * while strictly validating IANA timezone rules and Daylight Saving Time (DST) transitions.
 *
 * Enforces DD-13 and DD-23 contracts:
 * - Reject nonexistent local times caused by spring-forward DST gaps.
 * - Identify ambiguous local times during fall-back DST overlaps and allow explicit offset selection.
 * - Provide coherent string formatting combining local date/time, creation zone, and UTC offset.
 */
object TimeConversionHelper {

    private val DISPLAY_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)

    /**
     * Converts [localDate] and [localTime] within [zoneId] to an absolute UTC [Instant].
     *
     * DST Gap Handling:
     * When local time falls into a DST gap (spring-forward transition where clocks skip forward),
     * `validOffsets` is empty. This function rejects the time and returns [OperationResult.Invalid].
     *
     * DST Overlap Handling:
     * When local time falls into a DST overlap (fall-back transition where clocks repeat an hour),
     * `validOffsets` contains multiple choices. The sender MUST explicitly specify a valid [preferredOffset]
     * in `validOffsets`; otherwise, conversion returns [OperationResult.Invalid] requesting explicit offset selection.
     */
    fun toUtcInstant(
        localDate: LocalDate,
        localTime: LocalTime,
        zoneId: ZoneId,
        preferredOffset: ZoneOffset? = null
    ): OperationResult<TimeConversionResult> {
        val localDateTime = LocalDateTime.of(localDate, localTime)
        val rules = zoneId.rules
        val validOffsets = rules.getValidOffsets(localDateTime)

        if (validOffsets.isEmpty()) {
            // DST Gap: Clocks sprang forward, so this local time never occurred on the clock.
            val transition = rules.getTransition(localDateTime)
            val detail = if (transition != null) {
                "Time falls into DST gap (clocks spring forward from ${transition.dateTimeBefore.toLocalTime()} to ${transition.dateTimeAfter.toLocalTime()})."
            } else {
                "Time does not exist in timezone ${zoneId.id}."
            }
            return OperationResult.Invalid("Selected local time does not exist due to daylight saving transition: $detail")
        }

        if (validOffsets.size > 1 && (preferredOffset == null || preferredOffset !in validOffsets)) {
            // DST Overlap: Sender must explicitly select one of the valid offsets.
            val optionsStr = validOffsets.joinToString(" or ") { formatOffset(it) }
            return OperationResult.Invalid("Selected local time is ambiguous due to daylight saving transition. Please explicitly select an offset ($optionsStr).")
        }

        val chosenOffset = if (preferredOffset != null && preferredOffset in validOffsets) {
            preferredOffset
        } else {
            validOffsets[0]
        }

        if (preferredOffset != null && preferredOffset !in validOffsets) {
            return OperationResult.Invalid("Supplied offset ${formatOffset(preferredOffset)} is not valid for $localDateTime in $zoneId.")
        }

        val zonedDateTime = ZonedDateTime.ofLocal(localDateTime, zoneId, chosenOffset)
        return OperationResult.Success(
            TimeConversionResult(
                instant = zonedDateTime.toInstant(),
                zoneId = zoneId,
                offset = zonedDateTime.offset,
                validOffsets = validOffsets
            )
        )
    }

    /**
     * Formats an absolute UTC [instant] for user display using the specified [zoneId].
     * Example output: "2026-11-25 09:00 (Asia/Seoul, UTC+09:00)"
     */
    fun formatZonedDateTime(instant: Instant, zoneId: ZoneId): String {
        val zdt = instant.atZone(zoneId)
        val formattedDateTime = zdt.format(DISPLAY_FORMATTER)
        val offsetText = formatOffset(zdt.offset)
        return "$formattedDateTime (${zoneId.id}, $offsetText)"
    }

    /**
     * Formats a [ZoneOffset] into a clean ISO representation such as "UTC+09:00" or "UTC-05:00".
     */
    fun formatOffset(offset: ZoneOffset): String {
        val totalSeconds = offset.totalSeconds
        if (totalSeconds == 0) return "UTC+00:00"
        val prefix = if (totalSeconds < 0) "UTC-" else "UTC+"
        val absSeconds = abs(totalSeconds)
        val hours = absSeconds / 3600
        val minutes = (absSeconds % 3600) / 60
        return String.format(Locale.US, "%s%02d:%02d", prefix, hours, minutes)
    }
}
