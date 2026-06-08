package com.danzucker.stitchpad.feature.notification.push

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

actual class PushTokenProvider {
    actual suspend fun currentToken(): String? =
        runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()

    actual suspend fun invalidateToken() {
        runCatching { FirebaseMessaging.getInstance().deleteToken().await() }
    }
}
