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

    // Only the `.notDetermined` state presents a system dialog; requesting when the
    // status is already authorized/denied is a no-op. Confirm undetermined first and
    // report false otherwise, so the caller never consumes the one-shot pre-prompt
    // without a dialog actually appearing.
    override suspend fun requestPermission(): Boolean {
        val service = iosNativePushService ?: return false
        val undetermined = suspendCancellableCoroutine { cont ->
            service.authorizationUndetermined(
                BooleanCallback { value -> if (cont.isActive) cont.resume(value) }
            )
        }
        // Only request (and report a launched dialog) when the status is undetermined.
        if (undetermined) service.requestAuthorization()
        return undetermined
    }
}
