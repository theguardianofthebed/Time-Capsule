package com.example.timecapsule.data.recovery

import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.example.timecapsule.MainActivity
import com.example.timecapsule.TimeCapsuleApplication
import com.example.timecapsule.config.FirebaseConfig
import com.example.timecapsule.data.di.AppDependencyContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/**
 * Android Instrumentation test verifying that process-wide application dependencies,
 * session authority [com.example.timecapsule.data.session.SessionTracker], and [ForegroundRecoveryCoordinator] survive Activity recreation
 * (device rotation) without creating duplicate singletons, resetting session generation, or leaking state.
 */
@RunWith(AndroidJUnit4::class)
class ForegroundRecoveryLifecycleTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setupEmulatorConnection() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            if (Build.VERSION.SDK_INT >= 37) {
                InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                    context.packageName, "android.permission.ACCESS_LOCAL_NETWORK"
                )
            }
            FirebaseConfig.setupDebugEmulatorConnection(context, true)
        }
    }

    @get:Rule
    val grantLocalNetworkPermissionRule: TestRule =
        if (Build.VERSION.SDK_INT >= 37) {
            GrantPermissionRule.grant("android.permission.ACCESS_LOCAL_NETWORK")
        } else TestRule { statement, _ -> statement }

    @Test
    fun activityRecreation_preservesSingleSessionAuthorityAndCoordinatorInstance() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        var initialApplication: TimeCapsuleApplication? = null
        var initialContainer: AppDependencyContainer? = null
        var initialSessionGen: Long = -1L

        scenario.onActivity { activity ->
            val app = activity.application as TimeCapsuleApplication
            initialApplication = app
            initialContainer = app.container
            initialSessionGen = app.container.sessionTracker.currentSession().sessionGeneration
        }

        assertNotNull(initialApplication)
        assertNotNull(initialContainer)

        // Recreate Activity (simulating device configuration change / rotation)
        scenario.recreate()

        scenario.onActivity { activity ->
            val app = activity.application as TimeCapsuleApplication
            // Assert that application-scoped container, session tracker, and recovery coordinator are identical
            assertSame("Application instance must remain identical", initialApplication, app)
            assertSame("Container instance must remain identical across rotation", initialContainer, app.container)
            assertSame(
                "SessionTracker instance must remain identical",
                initialContainer?.sessionTracker,
                app.container.sessionTracker
            )
            assertSame(
                "RecoveryCoordinator instance must remain identical",
                initialContainer?.recoveryCoordinator,
                app.container.recoveryCoordinator
            )
            assertEquals(
                "Session generation must not reset or change during rotation",
                initialSessionGen,
                app.container.sessionTracker.currentSession().sessionGeneration
            )
        }

        scenario.close()
    }
}
