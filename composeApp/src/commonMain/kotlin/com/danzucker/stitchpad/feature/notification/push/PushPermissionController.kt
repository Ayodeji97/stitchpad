package com.danzucker.stitchpad.feature.notification.push

/**
 * Platform seam for the Android POST_NOTIFICATIONS runtime permission.
 *
 * The common code only calls this interface — all platform-specific APIs
 * (Manifest.permission, ActivityCompat, Build.VERSION) stay in androidMain.
 * The iOS implementation is a no-op stub.
 */
interface PushPermissionController {
    /**
     * True if we should surface the pre-prompt: Android 13+ AND POST_NOTIFICATIONS
     * not yet granted; on iOS, the OS authorization status is `.notDetermined`.
     *
     * `suspend` because iOS only exposes the authorization status asynchronously
     * (`getNotificationSettings`) — awaiting the real status avoids a cache-read race
     * where a stale value either over-shows the one-time pre-prompt (already
     * authorized/denied) or skips it entirely (fresh install). Android resolves
     * synchronously and returns immediately.
     */
    suspend fun shouldRequest(): Boolean

    /**
     * Launch the OS notification-permission dialog. No-op below Android 13 / on iOS.
     * Does nothing if [shouldRequest] is false.
     *
     * @return `true` if the OS dialog was actually launched; `false` if the call
     *   was a no-op (unsupported SDK version, no Activity, or iOS stub).
     */
    fun requestPermission(): Boolean
}
