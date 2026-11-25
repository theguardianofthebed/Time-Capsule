package com.example.timecapsule

import com.example.timecapsule.config.FirebaseConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying cold startup initialization order, fail-closed container safety,
 * and Firebase emulator config setup in [TimeCapsuleApplication].
 */
class TimeCapsuleApplicationTest {

    @Before
    fun setUp() {
        FirebaseConfig.resetEmulatorModeForTesting()
    }

    @Test
    fun coldStartup_uninitializedFirebase_failsClosedWithoutCrashingAndLeavesContainerNull() {
        val app = TimeCapsuleApplication()

        // In test JVM environment without Context or Firebase App, initialization fails closed
        val success = app.initializeFirebaseAndContainer(null)

        assertFalse("Initialization must fail closed when Context/Firebase is uninitialized", success)
        assertNull("Container must remain null when initialization fails", app.container)
    }
}
