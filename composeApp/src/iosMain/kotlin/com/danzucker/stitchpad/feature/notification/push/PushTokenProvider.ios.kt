package com.danzucker.stitchpad.feature.notification.push

import com.danzucker.stitchpad.di.iosNativePushService

actual class PushTokenProvider {
    actual suspend fun currentToken(): String? = iosNativePushService?.currentFcmToken()
    actual suspend fun invalidateToken() {
        iosNativePushService?.deleteToken()
    }
}
