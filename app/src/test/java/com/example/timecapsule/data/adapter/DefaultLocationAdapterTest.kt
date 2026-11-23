package com.example.timecapsule.data.adapter

import com.example.timecapsule.data.FakeLocationAdapter
import com.example.timecapsule.domain.model.ForegroundLocationSample
import com.example.timecapsule.domain.model.LocationSampleResult
import com.example.timecapsule.domain.model.LocationSampleStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [LocationAdapter] typed results and contract guarantees (T-18).
 */
class DefaultLocationAdapterTest {

    @Test
    fun fakeLocationAdapterReturnsTypedResults() = runBlocking {
        val sample = ForegroundLocationSample(
            latitude = 37.5665,
            longitude = 126.9780,
            elapsedRealtimeNanos = 1_000_000_000L,
            horizontalAccuracyMeters = 10f
        )
        val successAdapter = FakeLocationAdapter(
            fineGranted = true,
            mockResult = LocationSampleResult(
                status = LocationSampleStatus.SUCCESS,
                sample = sample
            )
        )

        assertTrue(successAdapter.isLocationAvailable())
        assertTrue(successAdapter.isFineLocationGranted())

        val successRes = successAdapter.getCurrentLocation()
        assertEquals(LocationSampleStatus.SUCCESS, successRes.status)
        assertNotNull(successRes.sample)
        assertEquals(37.5665, successRes.sample!!.latitude, 0.0001)

        val deniedAdapter = FakeLocationAdapter(
            fineGranted = false,
            mockResult = LocationSampleResult(
                status = LocationSampleStatus.PERMISSION_DENIED,
                errorMessage = "Fine location denied"
            )
        )

        assertFalse(deniedAdapter.isFineLocationGranted())
        val deniedRes = deniedAdapter.getCurrentLocation()
        assertEquals(LocationSampleStatus.PERMISSION_DENIED, deniedRes.status)
        assertNull(deniedRes.sample)
    }
}
