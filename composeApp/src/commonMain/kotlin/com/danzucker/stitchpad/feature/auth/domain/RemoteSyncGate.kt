package com.danzucker.stitchpad.feature.auth.domain

/**
 * Gate over the app's realtime sync layer (Firestore's watch streams).
 *
 * Exists for one reason: a server-side listener rejection that arrives while the
 * listener's collector coroutine is already being cancelled bypasses every
 * downstream `catch` and reaches the global handler as a fatal crash. Sign-out
 * creates exactly that window — auth dies (server starts rejecting every
 * user-scoped listen) at the same moment navigation tears the screens down
 * (cancelling those listeners' collectors). Reproduced on Android and iOS,
 * 2026-08-13.
 *
 * [quiesce] closes the streams FIRST, so by the time auth is cleared there is
 * nothing left for the server to reject. Both operations are idempotent.
 */
interface RemoteSyncGate {

    /** Close the server streams — listeners stay registered but go cache-only. */
    suspend fun quiesce()

    /** Reopen the server streams. */
    suspend fun resume()
}
