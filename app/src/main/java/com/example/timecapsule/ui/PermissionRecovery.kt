package com.example.timecapsule.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.fragment.app.Fragment

/** A previous request and a suppressed rationale together indicate the Settings recovery path. */
internal fun Fragment.needsPermissionSettings(permission: String): Boolean =
    requireContext().getSharedPreferences("permission_requests", Context.MODE_PRIVATE).getBoolean(permission, false) &&
        !shouldShowRequestPermissionRationale(permission)

/** Android uses the same rationale value before the first request and after a permanent denial. */
internal fun Fragment.requestOrOpenSettings(permission: String, settings: () -> Unit, request: () -> Unit) {
    val history = requireContext().getSharedPreferences("permission_requests", Context.MODE_PRIVATE)
    if (needsPermissionSettings(permission)) {
        settings()
    } else {
        history.edit().putBoolean(permission, true).apply()
        request()
    }
}

/** Open the current app's permission settings after Android stops presenting its dialog. */
internal fun Fragment.openAppPermissions() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", requireContext().packageName, null)))
}
