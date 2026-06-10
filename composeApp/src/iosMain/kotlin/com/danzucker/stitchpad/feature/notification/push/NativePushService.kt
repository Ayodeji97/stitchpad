package com.danzucker.stitchpad.feature.notification.push

/**
 * One-shot callback for the async [NativePushService.authorizationUndetermined] bridge.
 * A `fun interface` (not a `(Boolean) -> Unit` lambda) so the result crosses to Swift as a
 * plain `Bool` method parameter rather than a boxed `KotlinBoolean` closure argument.
 */
fun interface BooleanCallback {
    fun onResult(value: Boolean)
}

/**
 * Implemented by Swift (PushServiceIos), set on [iosNativePushService] from the AppDelegate
 * before doInitKoin. Bridges FirebaseMessaging + UNUserNotificationCenter calls that aren't
 * reachable from Kotlin/Native directly — mirrors NativeGoogleSignInLauncher.
 */
interface NativePushService {
    /** The latest FCM registration token Swift received, or null if not yet available. */
    fun currentFcmToken(): String?

    /**
     * Query whether iOS notification permission has not yet been requested
     * (`UNAuthorizationStatus.notDetermined`). Async because `getNotificationSettings`
     * is async-only — Swift invokes [callback] with the freshly-read status. The Kotlin
     * side awaits it (see IosPushPermissionController), so there is no stale cache to race.
     */
    fun authorizationUndetermined(callback: BooleanCallback)

    /** Request the iOS notification permission; on grant, register for remote notifications. */
    fun requestAuthorization()

    /** Delete the device's FCM token (Messaging.deleteToken) — used by sign-out invalidation. */
    fun deleteToken()
}
