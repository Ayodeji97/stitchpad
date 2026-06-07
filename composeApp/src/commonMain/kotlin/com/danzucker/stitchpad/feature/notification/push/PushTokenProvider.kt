package com.danzucker.stitchpad.feature.notification.push

/** Platform seam to fetch the current push token. GitLive has no messaging wrapper. */
expect class PushTokenProvider() {
    /** The current FCM/APNs registration token, or null if unavailable / not granted. */
    suspend fun currentToken(): String?

    /** Invalidate the device's current push token (local; offline-safe). Used on logout. */
    suspend fun invalidateToken()
}
