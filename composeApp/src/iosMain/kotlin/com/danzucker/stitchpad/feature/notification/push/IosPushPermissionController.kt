package com.danzucker.stitchpad.feature.notification.push

/**
 * iOS stub for [PushPermissionController].
 *
 * iOS notification permission is handled natively via UNUserNotificationCenter
 * (invoked by the FCM/APNs registration path), not through this interface.
 * This stub ensures the common binding is satisfied without any iOS-specific APIs
 * leaking into commonMain.
 */
class IosPushPermissionController : PushPermissionController {
    override fun shouldRequest(): Boolean = false
    override fun requestPermission() = Unit
}
