package com.danzucker.stitchpad.feature.notification.push

import com.danzucker.stitchpad.di.iosNativePushService

actual class PushTokenProvider {
    actual suspend fun currentToken(): String? = iosNativePushService?.currentFcmToken()

    /**
     * Fire-and-forget: Swift's Messaging.deleteToken invalidates the local token immediately,
     * but its server sync is async. This suspend call returns without awaiting full completion —
     * which is acceptable because the local token is what stops delivery; any stale server doc
     * is pruned server-side automatically once the token is marked UNREGISTERED.
     */
    actual suspend fun invalidateToken() {
        iosNativePushService?.deleteToken()
    }
}
