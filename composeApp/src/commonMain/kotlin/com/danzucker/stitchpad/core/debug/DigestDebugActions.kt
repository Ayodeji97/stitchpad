package com.danzucker.stitchpad.core.debug

import com.danzucker.stitchpad.core.logging.AppLogger
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import kotlinx.serialization.Serializable

/** Outcome of the debug "send daily digest now" trigger. */
sealed interface DigestSendResult {
    data object Sent : DigestSendResult
    data object Empty : DigestSendResult
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
                result.sent -> DigestSendResult.Sent
                result.reason == "disabled" -> DigestSendResult.Disabled
                result.reason == "empty" -> DigestSendResult.Empty
                else -> DigestSendResult.Failure(result.reason ?: "unknown")
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
