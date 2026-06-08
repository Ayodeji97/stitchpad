package com.danzucker.stitchpad.feature.auth.domain

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.notification.push.PushTokenRegistrar
import kotlinx.coroutines.withTimeoutOrNull

private const val UNREGISTER_TIMEOUT_MS = 3_000L

/**
 * Centralised sign-out sequence shared by every code path that can log the user out:
 *
 *  1. Capture the uid **before** the session is cleared.
 *  2. Bounded `unregisterForUser` — removes this device's token while still
 *     authenticated (owner-only Firestore rule rejects it post-sign-out).
 *  3. Bounded `invalidateToken` — forces any token doc we couldn't delete to
 *     become UNREGISTERED so the server prunes it on the next push, preventing
 *     the previous account's notifications reaching this device.
 *  4. `authRepository.signOut()` — clear the session, return the result.
 *
 * Both token operations are wrapped in [withTimeoutOrNull] so an offline logout
 * is never blocked longer than [UNREGISTER_TIMEOUT_MS].
 */
class SignOutUseCase(
    private val authRepository: AuthRepository,
    private val pushTokenRegistrar: PushTokenRegistrar,
) {
    suspend operator fun invoke(): Result<Unit, AuthError> {
        val userId = authRepository.getCurrentUser()?.id
        if (userId != null) {
            withTimeoutOrNull(UNREGISTER_TIMEOUT_MS) {
                pushTokenRegistrar.unregisterForUser(userId)
            }
        }
        withTimeoutOrNull(UNREGISTER_TIMEOUT_MS) {
            pushTokenRegistrar.invalidateToken()
        }
        return authRepository.signOut()
    }
}
