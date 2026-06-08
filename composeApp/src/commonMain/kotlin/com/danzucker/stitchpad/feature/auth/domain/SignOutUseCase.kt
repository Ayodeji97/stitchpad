package com.danzucker.stitchpad.feature.auth.domain

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.notification.push.PushTokenRegistrar
import kotlinx.coroutines.withTimeoutOrNull

private const val UNREGISTER_TIMEOUT_MS = 3_000L

/**
 * Centralised sign-out sequence shared by every code path that can log the user out:
 *
 *  1. `authRepository.signOut()` — clear the session, return the result.
 *  2. Only on confirmed success: bounded `invalidateToken` — forces the local FCM
 *     token to become UNREGISTERED so the old user's token doc can no longer
 *     deliver to this device. (The doc itself is pruned server-side on the next
 *     failed push via invalid-token pruning; we can't delete it post-sign-out
 *     anyway — the owner rule needs the now-cleared auth.) Bounded so an offline
 *     sign-out is never blocked longer than [UNREGISTER_TIMEOUT_MS].
 *
 * Token teardown intentionally happens AFTER a confirmed sign-out so that a
 * failed sign-out never leaves the session active with the token already gone/
 * rotated (which would stop push for the still-authenticated user).
 */
class SignOutUseCase(
    private val authRepository: AuthRepository,
    private val pushTokenRegistrar: PushTokenRegistrar,
) {
    suspend operator fun invoke(): Result<Unit, AuthError> {
        val result = authRepository.signOut()
        if (result is Result.Success) {
            // Only after a confirmed sign-out: invalidate the local FCM token so the old
            // user's token doc can no longer deliver to this device. (The doc itself is
            // pruned server-side on the next failed push via invalid-token pruning; we
            // can't delete it post-sign-out anyway — the owner rule needs the now-cleared
            // auth.) Bounded so offline sign-out isn't blocked.
            withTimeoutOrNull(UNREGISTER_TIMEOUT_MS) { pushTokenRegistrar.invalidateToken() }
        }
        return result
    }
}
