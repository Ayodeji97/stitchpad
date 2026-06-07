package com.danzucker.stitchpad.feature.notification.push

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.danzucker.stitchpad.feature.auth.data.CurrentActivityHolder

private const val PUSH_PERMISSION_REQUEST_CODE = 1001

/**
 * Android implementation of [PushPermissionController].
 *
 * Checks [Build.VERSION.SDK_INT] >= 33 (Android 13 / TIRAMISU) because
 * POST_NOTIFICATIONS is a runtime permission only from that version onward.
 * Older devices auto-grant notification permission, so no dialog is needed.
 *
 * The Activity reference is held via [CurrentActivityHolder] (a weak ref) —
 * the same mechanism used by [AndroidSsoCredentialProvider]. If the Activity
 * is gone when [requestPermission] fires (e.g. config change), the call is
 * silently dropped; the permission dialog will appear again on the next launch
 * because [hasAskedPushPermission] hasn't been set yet at that point.
 */
class AndroidPushPermissionController(
    private val context: Context,
    private val activityHolder: CurrentActivityHolder,
) : PushPermissionController {

    override fun shouldRequest(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    }

    override fun requestPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val activity = activityHolder.activity ?: return
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            PUSH_PERMISSION_REQUEST_CODE,
        )
    }
}
