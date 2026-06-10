package com.danzucker.stitchpad.core.debug

import com.danzucker.stitchpad.core.logging.AppLogger
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import kotlinx.serialization.Serializable

/** Outcome of the debug "send renewal reminder now" trigger. */
sealed interface ReminderSendResult {
    data class Sent(val to: String) : ReminderSendResult
    data class Failure(val reason: String) : ReminderSendResult
}

/** Debug-only: invokes the `debugSendMyRenewalReminder` Cloud Function for the signed-in account. */
interface ReminderDebugActions {
    suspend fun sendNow(): ReminderSendResult
}

@Serializable
private data class DebugReminderResultDto(
    val sent: Boolean = false,
    val to: String? = null,
)

private const val TAG = "ReminderDebugActions"

class DefaultReminderDebugActions(
    private val functions: FirebaseFunctions,
) : ReminderDebugActions {
    override suspend fun sendNow(): ReminderSendResult {
        return try {
            // no payload — the function reads the caller's uid from the Auth context server-side
            val result = functions
                .httpsCallable("debugSendMyRenewalReminder")
                .invoke()
                .data<DebugReminderResultDto>()
            if (result.sent) {
                ReminderSendResult.Sent(result.to ?: "")
            } else {
                ReminderSendResult.Failure("not_sent")
            }
        } catch (e: FirebaseFunctionsException) {
            AppLogger.e(tag = TAG, throwable = e) { "debugSendMyRenewalReminder failed: code=${e.code} ${e.message}" }
            ReminderSendResult.Failure(e.message ?: e.code.toString())
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            AppLogger.e(
                tag = TAG,
                throwable = e
            ) { "debugSendMyRenewalReminder threw: ${e::class.simpleName} ${e.message}" }
            ReminderSendResult.Failure(e.message ?: "network")
        }
    }
}
