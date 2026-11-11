package com.example.timecapsule.data

import com.example.timecapsule.data.session.DefaultSessionTracker
import com.example.timecapsule.data.session.SessionGuard
import com.example.timecapsule.domain.result.OperationResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class StaleSessionCallbackTest {

    @Test
    fun delayedOperationInFlightIsRejectedIfUserSignsOutBeforeCompletion() = runBlocking {
        val sessionTracker = DefaultSessionTracker()
        val sessionGuard = SessionGuard(sessionTracker)

        sessionTracker.startSession("user-account-a")
        val capturedSession = sessionTracker.currentSession()

        var callbackResult: OperationResult<String>? = null

        val job = launch {
            callbackResult = sessionGuard.runGuarded(capturedSession, operation = {
                delay(100)
                OperationResult.Success("Account A Private Data")
            })
        }

        // Account A signs out while request is in-flight
        delay(30)
        sessionTracker.clearSession()
        sessionTracker.startSession("user-account-b")

        job.join()

        assertTrue("Delayed callback from Account A must be rejected for Account B", callbackResult is OperationResult.Denied)
    }
}
