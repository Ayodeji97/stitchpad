package com.danzucker.stitchpad.feature.notification.push

interface PushTokenRepository {
    suspend fun registerToken(userId: String, token: String, platform: String = "android")
    suspend fun unregisterToken(userId: String, token: String)
}
