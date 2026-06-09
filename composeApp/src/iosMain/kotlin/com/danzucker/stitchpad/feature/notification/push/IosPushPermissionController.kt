package com.danzucker.stitchpad.feature.notification.push

import com.danzucker.stitchpad.di.iosNativePushService

class IosPushPermissionController : PushPermissionController {
    override fun shouldRequest(): Boolean =
        iosNativePushService?.authorizationUndetermined() ?: false

    override fun requestPermission(): Boolean {
        val service = iosNativePushService ?: return false
        service.requestAuthorization()
        return true
    }
}
