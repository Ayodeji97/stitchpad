package com.danzucker.stitchpad.feature.notification.push

import com.danzucker.stitchpad.core.logging.AppLogger

/** Fetches the device token and registers/unregisters it for the signed-in user. */
interface PushTokenRegistrar {
    /** Fetch the current token and register it (call after login / on dashboard load). */
    suspend fun registerForUser(userId: String)

    /** Register an already-known token (used by the messaging service's onNewToken). */
    suspend fun register(userId: String, token: String)

    /** Remove this device's token on logout. */
    suspend fun unregisterForUser(userId: String)
}

class DefaultPushTokenRegistrar(
    private val provider: PushTokenProvider,
    private val repository: PushTokenRepository,
) : PushTokenRegistrar {

    override suspend fun registerForUser(userId: String) {
        val token = provider.currentToken() ?: return
        runCatching { repository.registerToken(userId, token) }
            .onFailure { AppLogger.w { "push token register failed: ${it.message}" } }
    }

    override suspend fun register(userId: String, token: String) {
        runCatching { repository.registerToken(userId, token) }
            .onFailure { AppLogger.w { "push token register failed: ${it.message}" } }
    }

    override suspend fun unregisterForUser(userId: String) {
        val token = provider.currentToken() ?: return
        runCatching { repository.unregisterToken(userId, token) }
            .onFailure { AppLogger.w { "push token unregister failed: ${it.message}" } }
    }
}
