package com.example.timecapsule.ui.s05

import android.os.Bundle
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.timecapsule.R
import com.example.timecapsule.databinding.DialogS05MapTargetBinding
import com.example.timecapsule.domain.model.LocationTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android instrumentation tests for [S05MapTargetDialogFragment] exercising Correction B:
 * - Fragment result handoff via parentFragmentManager setFragmentResult
 * - Initial state without target disables Confirm synchronously
 * - Preselected initial target enables Confirm
 * - Unselected dialog with pending state preserves selection across recreation
 * - Cancel preserving host state
 */
@RunWith(AndroidJUnit4::class)
class S05MapTargetDialogFragmentTest {

    @Test
    fun newInstanceWithoutInitialTargetDisablesConfirmButtonSynchronously() {
        val scenario = launchFragmentInContainer<S05MapTargetDialogFragment>(
            fragmentArgs = null,
            themeResId = R.style.Theme_TimeCapsule
        )

        scenario.onFragment { fragment ->
            val binding = fragment.view?.let { DialogS05MapTargetBinding.bind(it) }
            assertFalse(binding?.btnConfirmLocation?.isEnabled == true)
        }
    }

    @Test
    fun newInstanceWithInitialTargetEnablesConfirmButtonSynchronously() {
        val target = LocationTarget(latitude = 37.5665, longitude = 126.9780, radiusMeters = 150.0)
        val args = S05MapTargetDialogFragment.newInstance(
            initialTarget = target,
            initialName = "Seoul Station",
            capsuleId = "capsule-101",
            sessionUid = "user-a",
            sessionGeneration = 1L,
            requestId = "req-123"
        ).arguments

        val scenario = launchFragmentInContainer<S05MapTargetDialogFragment>(
            fragmentArgs = args,
            themeResId = R.style.Theme_TimeCapsule
        )

        scenario.onFragment { fragment ->
            val binding = fragment.view?.let { DialogS05MapTargetBinding.bind(it) }
            assertTrue(binding?.btnConfirmLocation?.isEnabled == true)
        }
    }

    @Test
    fun confirmingTargetEmitsScopedFragmentResultBundle() {
        val target = LocationTarget(latitude = 37.5665, longitude = 126.9780, radiusMeters = 150.0)
        val args = S05MapTargetDialogFragment.newInstance(
            initialTarget = target,
            initialName = "37.5665, 126.9780",
            capsuleId = "capsule-101",
            sessionUid = "user-a",
            sessionGeneration = 1L,
            requestId = "req-123"
        ).arguments

        val scenario = launchFragmentInContainer<S05MapTargetDialogFragment>(
            fragmentArgs = args,
            themeResId = R.style.Theme_TimeCapsule
        )

        var resultBundle: Bundle? = null
        scenario.onFragment { fragment ->
            fragment.parentFragmentManager.setFragmentResultListener(
                S05MapTargetDialogFragment.REQUEST_KEY_MAP_TARGET,
                fragment
            ) { _, bundle ->
                resultBundle = bundle
            }

            val binding = fragment.view?.let { DialogS05MapTargetBinding.bind(it) }
            binding?.btnConfirmLocation?.performClick()
        }

        assertNotNull(resultBundle)
        val bundle = resultBundle!!
        assertEquals(37.5665, bundle.getDouble(S05MapTargetDialogFragment.RESULT_KEY_LATITUDE), 0.0001)
        assertEquals(126.9780, bundle.getDouble(S05MapTargetDialogFragment.RESULT_KEY_LONGITUDE), 0.0001)
        assertEquals(150.0, bundle.getDouble(S05MapTargetDialogFragment.RESULT_KEY_RADIUS), 0.01)
        assertEquals("37.5665, 126.9780", bundle.getString(S05MapTargetDialogFragment.RESULT_KEY_DISPLAY_NAME))
        assertEquals("capsule-101", bundle.getString(S05MapTargetDialogFragment.RESULT_KEY_CAPSULE_ID))
        assertEquals("user-a", bundle.getString(S05MapTargetDialogFragment.RESULT_KEY_SESSION_UID))
        assertEquals(1L, bundle.getLong(S05MapTargetDialogFragment.RESULT_KEY_SESSION_GENERATION))
        assertEquals("req-123", bundle.getString(S05MapTargetDialogFragment.RESULT_KEY_REQUEST_ID))
    }

    @Test
    fun cancellingDialogEmitsNoResult() {
        val target = LocationTarget(latitude = 37.5665, longitude = 126.9780, radiusMeters = 150.0)
        val args = S05MapTargetDialogFragment.newInstance(target, "Seoul").arguments

        val scenario = launchFragmentInContainer<S05MapTargetDialogFragment>(
            fragmentArgs = args,
            themeResId = R.style.Theme_TimeCapsule
        )

        var resultBundle: Bundle? = null
        scenario.onFragment { fragment ->
            fragment.parentFragmentManager.setFragmentResultListener(
                S05MapTargetDialogFragment.REQUEST_KEY_MAP_TARGET,
                fragment
            ) { _, bundle ->
                resultBundle = bundle
            }

            val binding = fragment.view?.let { DialogS05MapTargetBinding.bind(it) }
            binding?.btnCloseMap?.performClick()
        }

        assertNull(resultBundle)
    }

    @Test
    fun stateRestorationPreservesPendingSelectionAcrossRecreation() {
        val target = LocationTarget(latitude = 37.5665, longitude = 126.9780, radiusMeters = 150.0)
        val args = S05MapTargetDialogFragment.newInstance(target, "Seoul").arguments

        val scenario = launchFragmentInContainer<S05MapTargetDialogFragment>(
            fragmentArgs = args,
            themeResId = R.style.Theme_TimeCapsule
        )

        scenario.recreate()

        scenario.onFragment { fragment ->
            val binding = fragment.view?.let { DialogS05MapTargetBinding.bind(it) }
            assertTrue(binding?.btnConfirmLocation?.isEnabled == true)
        }
    }
}
