package com.danzucker.stitchpad.feature.auth.domain

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.notification.push.PushTokenRegistrar
import kotlinx.coroutines.withTimeoutOrNull

private const val UNREGISTER_TIMEOUT_MS = 3_000L

/**
 * Centralised sign-out sequence shared by every code path that can log the user out:
 *
 *  1. Delete this device's `notificationTokens/{token}` doc WHILE STILL AUTHENTICATED —
 *     the owner-only Firestore rule requires auth, so it cannot be done after sign-out.
 *     Best-effort + bounded so an offline sign-out is never blocked.
 *  2. `authRepository.signOut()` — clear the session, return the result.
 *  3. ONLY on confirmed success: bounded `invalidateToken` — rotate the local FCM token
 *     so any doc that step 1 couldn't delete (offline/transient) becomes UNREGISTERED
 *     and is pruned server-side on the next push.
 *
 * Step 1 runs before sign-out because the delete needs auth; step 3 runs only AFTER a
 * confirmed sign-out so a failed sign-out never rotates the token of a still-active
 * session (which would stop push for the still-authenticated user). Both are bounded by
 * [UNREGISTER_TIMEOUT_MS]. The residual offline edge (both ops time out) is closed by the
 * server-side token-ownership cleanup tracked as a pre-rollout follow-up.
 */
class SignOutUseCase(
    private val authRepository: AuthRepository,
    private val pushTokenRegistrar: PushTokenRegistrar,
) {
    suspend operator fun invoke(): Result<Unit, AuthError> {
        // Owner-authorised token-doc removal must happen before the session is cleared.
        val userId = authRepository.getCurrentUser()?.id
        if (userId != null) {
            withTimeoutOrNull(UNREGISTER_TIMEOUT_MS) { pushTokenRegistrar.unregisterForUser(userId) }
        }
        val result = authRepository.signOut()
        if (result is Result.Success) {
            // Backup only on confirmed sign-out: rotate the local FCM token so any doc the
            // step above couldn't delete (offline) gets pruned server-side on the next push.
            withTimeoutOrNull(UNREGISTER_TIMEOUT_MS) { pushTokenRegistrar.invalidateToken() }
        }
        return result
    }
}
