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
     * Launch the OS notification-permission dialog. No-op below Android 13.
     * `suspend` so iOS can confirm the authorization status is `.notDetermined`
     * before requesting — the system dialog only appears when undetermined, so
     * returning `true` otherwise would falsely claim a dialog was shown.
     *
     * @return `true` if the OS dialog was actually launched; `false` if the call
     *   was a no-op (unsupported Android SDK, no Activity, iOS status already
     *   determined, or the iOS bridge is unset). Callers must NOT mark the
     *   one-shot pre-prompt as "asked" when this returns `false`.
     */
    suspend fun requestPermission(): Boolean
}
