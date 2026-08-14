package com.danzucker.stitchpad.feature.auth.data

import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.auth.domain.AuthError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

private const val TAG = "AuthSessionValidator"

/**
 * Detects zombie auth sessions and forces a clean local sign-out.
 *
 * Firebase keeps the cached user object even after the refresh token dies
 * (account deleted/recreated by test tooling, disabled, or password changed) —
 * the app then renders authenticated UI while every token-dependent call fails
 * or hangs: Firestore streams close UNAUTHENTICATED forever and callables never
 * complete. Field incident 2026-08-14: the staff redeem screen froze on its
 * spinner for exactly this reason, with 604 silent INVALID_REFRESH_TOKEN
 * failures in the session log.
 *
 * On every sign-in (including app start with a cached session) this forces one
 * token refresh. Only [AuthError.USER_NOT_FOUND] — the mapping for
 * invalid-user/invalid-refresh-token — triggers sign-out; transient failures
 * (network, backend) leave the session untouched, so a cold start in a dead
 * zone can never eject a valid user.
 */
class AuthSessionValidator(
    private val userIdSource: Flow<String?>,
    private val validateSession: suspend () -> EmptyResult<AuthError>,
    private val signOut: suspend () -> Unit,
    private val scope: CoroutineScope,
) {
    fun start() {
        userIdSource
            .distinctUntilChanged()
            .onEach { userId -> if (userId != null) validate() }
            .launchIn(scope)
    }

    private suspend fun validate() {
        val result = validateSession()
        if (result is Result.Error && result.error == AuthError.USER_NOT_FOUND) {
            AppLogger.w(tag = TAG) { "auth session invalid (${result.error}); forcing local sign-out" }
            signOut()
        }
    }
}
