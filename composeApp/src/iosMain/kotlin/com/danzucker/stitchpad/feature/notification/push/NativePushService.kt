package com.danzucker.stitchpad.feature.notification.push

/**
 * Implemented by Swift (PushServiceIos), set on [iosNativePushService] from the AppDelegate
 * before doInitKoin. Bridges FirebaseMessaging + UNUserNotificationCenter calls that aren't
 * reachable from Kotlin/Native directly — mirrors NativeGoogleSignInLauncher.
 */
interface NativePushService {
    /** The latest FCM registration token Swift received, or null if not yet available. */
    fun currentFcmToken(): String?

    /** True when iOS notification permission has not yet been requested (UNAuthorizationStatus.notDetermined). */
    fun authorizationUndetermined(): Boolean

    /** Request the iOS notification permission; on grant, register for remote notifications. */
    fun requestAuthorization()

    /** Delete the device's FCM token (Messaging.deleteToken) — used by sign-out invalidation. */
    fun deleteToken()
}
