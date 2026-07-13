package com.danzucker.stitchpad.core.debug

import com.danzucker.stitchpad.core.logging.AppLogger
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.FunctionsExceptionCode

/**
 * Admin-only (Group B) referral debug actions. Each invokes an already-deployed
 * debug Cloud Function that runs the corresponding nightly job's exact handler.
 * These are `admin:true`-gated server-side, so a normal tester gets a clear
 * "Admin only" failure rather than a silent no-op. No new server code — this is
 * just an in-app trigger so the whole lifecycle is drivable from a debug build.
 */
interface ReferralAdminDebugActions {
    /** debugReconcileReferrals — grades in-window referrals (attributed → qualified → pending). */
    suspend fun runGrader(): DebugActionResult

    /** debugConfirmReferralPayouts — releases held payouts (pending → confirmed / rejected). */
    suspend fun runConfirmPayouts(): DebugActionResult

    /** debugSweepDeletedReferralUsers — claws back payouts for deleted referred users. */
    suspend fun runSweep(): DebugActionResult
}

private const val TAG = "ReferralAdminDebug"

class DefaultReferralAdminDebugActions(
    private val functions: FirebaseFunctions,
) : ReferralAdminDebugActions {

    override suspend fun runGrader(): DebugActionResult =
        callDebug("debugReconcileReferrals")

    override suspend fun runConfirmPayouts(): DebugActionResult =
        callDebug("debugConfirmReferralPayouts")

    override suspend fun runSweep(): DebugActionResult =
        callDebug("debugSweepDeletedReferralUsers")

    private suspend fun callDebug(name: String): DebugActionResult {
        return try {
            // No payload — the function operates over the referral collections and
            // reads the caller's admin claim from the Auth context server-side.
            val result = functions.httpsCallable(name).invoke()
            AppLogger.i(tag = TAG) { "$name ok: ${result.data<String?>() ?: "done"}" }
            DebugActionResult.Success
        } catch (e: FirebaseFunctionsException) {
            AppLogger.e(tag = TAG, throwable = e) { "$name failed: code=${e.code} ${e.message}" }
            val reason = if (e.code == FunctionsExceptionCode.PERMISSION_DENIED) {
                "Admin only — sign in with an admin account"
            } else {
                e.message ?: e.code.toString()
            }
            DebugActionResult.Failure(reason)
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            AppLogger.e(tag = TAG, throwable = e) { "$name threw: ${e::class.simpleName} ${e.message}" }
            DebugActionResult.Failure(e.message ?: "network")
        }
    }
}
