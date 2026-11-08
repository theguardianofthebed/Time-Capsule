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
 * Confirms that [MainActivity] launches successfully with its AppCompat theme and displays [R.id.tvMainTitle].
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    /**
     * Verifies that [MainActivity] launches without crashing and displays the main title view.
     */
    @Test
    fun mainActivity_launchesAndDisplaysTitle() {
        // Launch MainActivity using ActivityScenario to verify lifecycle creation under Theme.TimeCapsule
        ActivityScenario.launch(MainActivity::class.java).use {
            // Verify tvMainTitle is visible on screen
            onView(withId(R.id.tvMainTitle)).check(matches(isDisplayed()))
        }
    }
}
