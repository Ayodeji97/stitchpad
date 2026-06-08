package com.danzucker.stitchpad.core.debug

import com.danzucker.stitchpad.core.logging.AppLogger
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import kotlinx.serialization.Serializable

/** Outcome of the debug "send daily digest now" trigger. */
sealed interface DigestSendResult {
    /** At least one channel (email or push) delivered something. */
    data class Sent(val emailSent: Boolean, val pushSent: Boolean) : DigestSendResult

    /** Digest model was empty — no actionable orders. */
    data object Empty : DigestSendResult

    /** Both channels disabled (opted out) but there were actionable orders. */
    data object Disabled : DigestSendResult

    data class Failure(val reason: String) : DigestSendResult
}

/** Debug-only: invokes the `debugSendMyDigest` Cloud Function for the signed-in account. */
interface DigestDebugActions {
    suspend fun sendNow(): DigestSendResult
}

@Serializable
private data class DebugDigestResultDto(
    val sent: Boolean = false,
    val reason: String? = null,
    val emailSent: Boolean = false,
    val pushSent: Boolean = false,
)

private const val TAG = "DigestDebugActions"

class DefaultDigestDebugActions(
    private val functions: FirebaseFunctions,
) : DigestDebugActions {
    override suspend fun sendNow(): DigestSendResult {
        return try {
            // no payload — the function reads the caller's uid from the Auth context server-side
            val result = functions
                .httpsCallable("debugSendMyDigest")
                .invoke()
                .data<DebugDigestResultDto>()
            when {
                result.sent || result.emailSent || result.pushSent ->
                    DigestSendResult.Sent(emailSent = result.emailSent, pushSent = result.pushSent)
                result.reason == "empty" -> DigestSendResult.Empty
                // sent=false with no "empty" reason means both channels were disabled
                else -> DigestSendResult.Disabled
            }
        } catch (e: FirebaseFunctionsException) {
            AppLogger.e(tag = TAG, throwable = e) { "debugSendMyDigest failed: code=${e.code} ${e.message}" }
            DigestSendResult.Failure(e.message ?: e.code.toString())
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            AppLogger.e(tag = TAG, throwable = e) { "debugSendMyDigest threw: ${e::class.simpleName} ${e.message}" }
            DigestSendResult.Failure(e.message ?: "network")
        }
    }
}
