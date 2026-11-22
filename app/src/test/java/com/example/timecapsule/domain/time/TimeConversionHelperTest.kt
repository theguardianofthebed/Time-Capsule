package com.example.timecapsule.domain.time

import com.example.timecapsule.domain.result.OperationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * JVM Unit tests verifying [TimeConversionHelper] UTC conversion, Daylight Saving Time (DST)
 * gap rejection, DST fall-back overlap resolution, and coherent timezone string formatting.
 */
class TimeConversionHelperTest {

    @Test
    fun `toUtcInstant converts standard local date and time to exact UTC Instant`() {
        val date = LocalDate.of(2026, 11, 25)
        val time = LocalTime.of(9, 0)
        val zoneId = ZoneId.of("Asia/Seoul") // UTC+9

        val result = TimeConversionHelper.toUtcInstant(date, time, zoneId)

        assertTrue(result is OperationResult.Success)
        val data = (result as OperationResult.Success).data
        val expectedInstant = Instant.parse("2026-11-25T00:00:00Z")
        assertEquals(expectedInstant, data.instant)
        assertEquals(zoneId, data.zoneId)
        assertEquals(ZoneOffset.ofHours(9), data.offset)
        assertFalse(data.isAmbiguousOverlap)
    }

    @Test
    fun `toUtcInstant rejects nonexistent local time in DST spring-forward gap`() {
        // Clocks in US/Eastern spring forward from 02:00 to 03:00 on March 8, 2026.
        // 02:30 AM does not exist on the clock.
        val date = LocalDate.of(2026, 3, 8)
        val time = LocalTime.of(2, 30)
        val zoneId = ZoneId.of("America/New_York")

        val result = TimeConversionHelper.toUtcInstant(date, time, zoneId)

        assertTrue(result is OperationResult.Invalid)
        val message = (result as OperationResult.Invalid).message
        assertTrue(message.contains("daylight saving transition"))
    }

    @Test
    fun `toUtcInstant detects DST fall-back overlap and allows preferred offset`() {
        // Clocks in US/Eastern fall back from 02:00 to 01:00 on November 1, 2026.
        // 01:30 AM occurs twice: first at UTC-4 (EDT), then at UTC-5 (EST).
        val date = LocalDate.of(2026, 11, 1)
        val time = LocalTime.of(1, 30)
        val zoneId = ZoneId.of("America/New_York")

        // 1. Without preferred offset: returns valid offsets and defaults to earlier offset (EDT, UTC-4)
        val resultDefault = TimeConversionHelper.toUtcInstant(date, time, zoneId)
        assertTrue(resultDefault is OperationResult.Success)
        val dataDefault = (resultDefault as OperationResult.Success).data
        assertTrue(dataDefault.isAmbiguousOverlap)
        assertEquals(2, dataDefault.validOffsets.size)
        assertEquals(ZoneOffset.ofHours(-4), dataDefault.offset)

        // 2. With preferred offset (EST, UTC-5): uses explicit preferred offset
        val resultEst = TimeConversionHelper.toUtcInstant(
            date, time, zoneId, preferredOffset = ZoneOffset.ofHours(-5)
        )
        assertTrue(resultEst is OperationResult.Success)
        val dataEst = (resultEst as OperationResult.Success).data
        assertEquals(ZoneOffset.ofHours(-5), dataEst.offset)
    }

    @Test
    fun `timezone conversion does not alter original UTC Instant`() {
        val utcInstant = Instant.parse("2026-11-25T12:00:00Z")
        val seoulZone = ZoneId.of("Asia/Seoul")
        val nyZone = ZoneId.of("America/New_York")

        val seoulFormatted = TimeConversionHelper.formatZonedDateTime(utcInstant, seoulZone)
        val nyFormatted = TimeConversionHelper.formatZonedDateTime(utcInstant, nyZone)

        assertEquals("2026-11-25 21:00 (Asia/Seoul, UTC+09:00)", seoulFormatted)
        assertEquals("2026-11-25 07:00 (America/New_York, UTC-05:00)", nyFormatted)
    }

    @Test
    fun `formatOffset correctly formats zero positive and negative offsets`() {
        assertEquals("UTC+00:00", TimeConversionHelper.formatOffset(ZoneOffset.UTC))
        assertEquals("UTC+09:00", TimeConversionHelper.formatOffset(ZoneOffset.ofHours(9)))
        assertEquals("UTC-05:00", TimeConversionHelper.formatOffset(ZoneOffset.ofHours(-5)))
        assertEquals("UTC+05:30", TimeConversionHelper.formatOffset(ZoneOffset.ofHoursMinutes(5, 30)))
    }
}
