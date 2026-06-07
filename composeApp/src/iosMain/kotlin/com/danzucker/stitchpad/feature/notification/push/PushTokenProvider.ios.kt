package com.danzucker.stitchpad.feature.notification.push

// iOS APNs is a fast-follow slice — this stub keeps the KMP build green.
actual class PushTokenProvider {
    actual suspend fun currentToken(): String? = null

    @Suppress("EmptyFunctionBlock")
    actual suspend fun invalidateToken() {}
}
