package com.example.timecapsule

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented regression test for [MainActivity] theme and activity launch verification.
 * Confirms that [MainActivity] launches successfully with its AppCompat theme and hosts [R.id.fragmentContainer].
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    /**
     * Verifies that [MainActivity] launches without crashing and displays the fragment container.
     */
    @Test
    fun mainActivity_launchesAndHostsFragmentContainer() {
        // Launch MainActivity using ActivityScenario to verify lifecycle creation under Theme.TimeCapsule
        ActivityScenario.launch(MainActivity::class.java).use {
            // Verify fragmentContainer is visible on screen
            onView(withId(R.id.fragmentContainer)).check(matches(isDisplayed()))
        }
    }
}
