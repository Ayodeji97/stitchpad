package com.danzucker.stitchpad.feature.auth.domain

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.staff.StaffMembershipPrefsStore
import com.danzucker.stitchpad.feature.notification.push.PushTokenRegistrar
import com.danzucker.stitchpad.navigation.PendingDeepLinkHolder
import kotlinx.coroutines.withTimeoutOrNull

private const val UNREGISTER_TIMEOUT_MS = 3_000L
private const val QUIESCE_TIMEOUT_MS = 3_000L

/**
 * Centralised sign-out sequence shared by every code path that can log the user out:
 *
 *  1. Delete this device's `notificationTokens/{token}` doc WHILE STILL AUTHENTICATED —
 *     the owner-only Firestore rule requires auth, so it cannot be done after sign-out.
 *     Best-effort + bounded so an offline sign-out is never blocked.
 *  2. [RemoteSyncGate.quiesce] — close Firestore's watch streams BEFORE clearing auth.
 *     A listener the server rejects (auth just died) while sign-out navigation is
 *     cancelling its collector bypasses every downstream `catch` and crashes the app
 *     (reproduced on both platforms 2026-08-13). With the streams closed there is
 *     nothing left to reject. Best-effort + bounded, like step 1.
 *  3. `authRepository.signOut()` — clear the session, return the result.
 *  4. ONLY on confirmed success: bounded `invalidateToken` — rotate the local FCM token
 *     so any doc that step 1 couldn't delete (offline/transient) becomes UNREGISTERED
 *     and is pruned server-side on the next push. The gate stays closed and reopens
 *     itself on the next sign-in (see [RemoteSyncGate]).
 *     On FAILURE: reopen the gate immediately — the still-authenticated user stays in
 *     the app, and their listeners re-attach against a live session.
 *
 * Step 1 runs before sign-out because the delete needs auth; step 4's invalidate runs
 * only AFTER a confirmed sign-out so a failed sign-out never rotates the token of a
 * still-active session (which would stop push for the still-authenticated user). The
 * residual offline edge (ops time out) is closed by the server-side token-ownership
 * cleanup tracked as a pre-rollout follow-up.
 */
class SignOutUseCase(
    private val authRepository: AuthRepository,
    private val pushTokenRegistrar: PushTokenRegistrar,
    private val pendingDeepLink: PendingDeepLinkHolder,
    private val staffMembershipPrefs: StaffMembershipPrefsStore,
    private val remoteSyncGate: RemoteSyncGate,
) {
    suspend operator fun invoke(): Result<Unit, AuthError> {
        // Owner-authorised token-doc removal must happen before the session is cleared.
        val userId = authRepository.getCurrentUser()?.id
        if (userId != null) {
            withTimeoutOrNull(UNREGISTER_TIMEOUT_MS) { pushTokenRegistrar.unregisterForUser(userId) }
        }
        // Close the watch streams before auth dies — see step 2 in the class kdoc.
        runCatching { withTimeoutOrNull(QUIESCE_TIMEOUT_MS) { remoteSyncGate.quiesce() } }
        val result = authRepository.signOut()
        if (result is Result.Success) {
            // Drop any pending deep-link target AND its payload (an UPGRADE email-link
            // tap's pre-select, or a CLAIM_GIFT link's bearer code) so it can't
            // auto-route / pre-select / be claimed by the NEXT user who signs in on this
            // device — the link belonged to the user who just signed out.
            pendingDeepLink.clear()
            pendingDeepLink.consumeUpgradePreselect()
            pendingDeepLink.consumeClaimGiftCode()
            pendingDeepLink.consumeJoinWorkshopCode()
            // Clear the staff pending-window uid centrally, so the NEXT user who
            // signs in on this device never inherits a stale joined-workshop id
            // (covers the Home/Settings sign-out path, not just the staff screens).
            staffMembershipPrefs.clear()
            // Backup only on confirmed sign-out: rotate the local FCM token so any doc the
            // step above couldn't delete (offline) gets pruned server-side on the next push.
            withTimeoutOrNull(UNREGISTER_TIMEOUT_MS) { pushTokenRegistrar.invalidateToken() }
        } else {
            // Sign-out failed: the user is still authenticated and stays in the app —
            // reopen the streams so their data goes live again.
            runCatching { remoteSyncGate.resume() }
        }
        return result
    }
}
