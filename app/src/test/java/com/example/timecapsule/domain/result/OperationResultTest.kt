package com.example.timecapsule.domain.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [OperationResult] sealed hierarchy variants.
 */
class OperationResultTest {

    @Test
    fun successCarriesData() {
        val result: OperationResult<String> = OperationResult.Success("test-payload")
        assertEquals("test-payload", (result as OperationResult.Success).data)
    }

    @Test
    fun invalidCarriesMessage() {
        val result: OperationResult<Nothing> = OperationResult.Invalid("invalid parameter")
        assertEquals("invalid parameter", (result as OperationResult.Invalid).message)
    }

    @Test
    fun deniedCarriesMessage() {
        val result: OperationResult<Nothing> = OperationResult.Denied("unauthorized session")
        assertEquals("unauthorized session", (result as OperationResult.Denied).message)
    }

    @Test
    fun unavailableCarriesMessage() {
        val result: OperationResult<Nothing> = OperationResult.Unavailable("service unreachable")
        assertEquals("service unreachable", (result as OperationResult.Unavailable).message)
    }

    @Test
    fun outcomeUnknownCarriesCapsuleIdAndMessage() {
        val result: OperationResult<Nothing> = OperationResult.OutcomeUnknown("capsule-101", "write unconfirmed")
        val unknown = result as OperationResult.OutcomeUnknown
        assertEquals("capsule-101", unknown.capsuleId)
        assertEquals("write unconfirmed", unknown.message)
    }
}
