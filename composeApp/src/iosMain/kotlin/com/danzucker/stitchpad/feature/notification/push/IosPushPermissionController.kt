package com.danzucker.stitchpad.feature.notification.push

import com.danzucker.stitchpad.di.iosNativePushService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class IosPushPermissionController : PushPermissionController {
    // Awaits the OS authorization status (read async via getNotificationSettings on the
    // Swift side) instead of a pre-seeded cache, so the dashboard pre-prompt decision
    // always sees the real status — no over-/under-prompt race.
    override suspend fun shouldRequest(): Boolean {
        val service = iosNativePushService ?: return false
        return suspendCancellableCoroutine { cont ->
            service.authorizationUndetermined(
                BooleanCallback { value -> if (cont.isActive) cont.resume(value) }
            )
        }
    }

    override fun requestPermission(): Boolean {
        val service = iosNativePushService ?: return false
        service.requestAuthorization()
        return true
    }
}
