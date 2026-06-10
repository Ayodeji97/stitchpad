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
 * One-shot callback for the async [NativePushService.currentFcmToken] bridge.
 * Carries the FCM token (or null when unavailable) back from Swift's Messaging.token.
 */
fun interface FcmTokenCallback {
    fun onResult(value: String?)
}

/**
 * Implemented by Swift (PushServiceIos), set on [iosNativePushService] from the AppDelegate
 * before doInitKoin. Bridges FirebaseMessaging + UNUserNotificationCenter calls that aren't
 * reachable from Kotlin/Native directly — mirrors NativeGoogleSignInLauncher.
 */
interface NativePushService {
    /**
     * Fetch the current FCM registration token from Firebase (Messaging.token), minting a
     * fresh one if needed. Async (callback) and live — NOT a stored cache — so the pull path
     * recovers a token after sign-out/deleteToken without waiting for an app restart. Swift
     * invokes [callback] with the token, or null on error.
     */
    fun currentFcmToken(callback: FcmTokenCallback)

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
