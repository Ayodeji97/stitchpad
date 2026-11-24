package com.danzucker.stitchpad.feature.freemium.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.freemium.domain.PaymentError
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.FunctionsExceptionCode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

private const val TAG = "PlayVerifier"

/**
 * Server verdict for one Play purchase token (functions/src/billing/playBilling.ts).
 * BLOCKED: valid and saved, but another active entitlement still owns the plan.
 */
enum class PlayVerifyStatus { ACTIVE, BLOCKED, PENDING, EXPIRED }

/** Maps the verifyPlayPurchase response fields to a [PlayVerifyStatus]. */
internal fun playVerifyStatus(status: String, blocked: Boolean): PlayVerifyStatus = when (status) {
    "active" -> if (blocked) PlayVerifyStatus.BLOCKED else PlayVerifyStatus.ACTIVE
    "pending" -> PlayVerifyStatus.PENDING
    else -> PlayVerifyStatus.EXPIRED
}

/** Sends a Play purchase token to the `verifyPlayPurchase` callable. A seam so the repository is testable. */
fun interface PlayPurchaseVerifier {
    suspend fun verify(purchaseToken: String): Result<PlayVerifyStatus, PaymentError>
}

internal class CloudFunctionsPlayPurchaseVerifier(
    private val functions: FirebaseFunctions,
) : PlayPurchaseVerifier {

    override suspend fun verify(purchaseToken: String): Result<PlayVerifyStatus, PaymentError> {
        return try {
            val response = functions
                .httpsCallable("verifyPlayPurchase")
                .invoke(VerifyPlayPurchaseRequestDto(purchaseToken = purchaseToken))
                .data<VerifyPlayPurchaseResponseDto>()
            Result.Success(playVerifyStatus(response.status, response.blocked))
        } catch (e: FirebaseFunctionsException) {
            AppLogger.e(tag = TAG, throwable = e) { "verifyPlayPurchase failed: code=${e.code} message=${e.message}" }
            val kind = when (e.code) {
                FunctionsExceptionCode.UNAUTHENTICATED -> CallableErrorKind.UNAUTHENTICATED
                FunctionsExceptionCode.UNAVAILABLE -> CallableErrorKind.UNAVAILABLE
                else -> CallableErrorKind.OTHER
            }
            Result.Error(playVerifyError(kind, e.message))
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            AppLogger.e(tag = TAG, throwable = e) { "verifyPlayPurchase threw ${e::class.simpleName}: ${e.message}" }
            Result.Error(recoverPaymentError(e.message, fallback = PaymentError.NETWORK))
        }
    }
}

/**
 * The callable error codes the mapping cares about. A local enum, not GitLive's
 * FunctionsExceptionCode, so the mapping stays testable on the plain JVM.
 */
internal enum class CallableErrorKind { UNAUTHENTICATED, UNAVAILABLE, OTHER }

// Server message marker from playBilling.ts (Play Developer API unreachable).
private const val MARKER_PLAY_UNAVAILABLE = "play_unavailable"

/**
 * Maps a verifyPlayPurchase failure. account_mismatch, test_purchase_not_allowed
 * and subscription_belongs_to_another_account are not recoverable by the user,
 * so they surface as UNKNOWN; an unreachable Play API is PROVIDER_UNAVAILABLE.
 */
internal fun playVerifyError(kind: CallableErrorKind?, message: String?): PaymentError = when {
    kind == CallableErrorKind.UNAUTHENTICATED -> PaymentError.UNAUTHENTICATED
    kind == CallableErrorKind.UNAVAILABLE -> PaymentError.PROVIDER_UNAVAILABLE
    message?.contains(MARKER_PLAY_UNAVAILABLE) == true -> PaymentError.PROVIDER_UNAVAILABLE
    else -> recoverPaymentError(message, fallback = PaymentError.UNKNOWN)
}

@Serializable
private data class VerifyPlayPurchaseRequestDto(val purchaseToken: String)

@Serializable
private data class VerifyPlayPurchaseResponseDto(
    val tier: String = "free",
    val status: String = "expired",
    val endsAt: Long? = null,
    val blocked: Boolean = false,
)
