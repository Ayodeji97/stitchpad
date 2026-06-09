package com.danzucker.stitchpad.feature.freemium.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.freemium.domain.BillingCadence
import com.danzucker.stitchpad.feature.freemium.domain.CheckoutSession
import com.danzucker.stitchpad.feature.freemium.domain.PaymentError
import com.danzucker.stitchpad.feature.freemium.domain.PaymentRepository
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.FunctionsExceptionCode
import kotlinx.serialization.Serializable

private const val TAG = "PaymentRepo"

internal class CloudFunctionsPaymentRepository(
    private val functions: FirebaseFunctions,
) : PaymentRepository {

    override suspend fun initializeSubscriptionCheckout(
        tier: SubscriptionTier,
        cadence: BillingCadence,
    ): Result<CheckoutSession, PaymentError> {
        return try {
            val response = functions
                .httpsCallable("initializeSubscriptionCheckout")
                .invoke(
                    InitializeCheckoutRequestDto(
                        tier = tier.wireValue,
                        cadence = cadence.wireValue,
                    )
                )
                .data<InitializeCheckoutResponseDto>()
            Result.Success(
                CheckoutSession(
                    authorizationUrl = response.authorizationUrl,
                    reference = response.reference,
                )
            )
        } catch (e: FirebaseFunctionsException) {
            AppLogger.e(tag = TAG, throwable = e) {
                "initializeSubscriptionCheckout failed: code=${e.code} message=${e.message}"
            }
            Result.Error(mapFunctionsError(e))
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            AppLogger.e(tag = TAG, throwable = e) {
                "initializeSubscriptionCheckout threw ${e::class.simpleName}: ${e.message}"
            }
            // GitLive on iOS can surface the callable HttpsError as a plain
            // Throwable with the canonical code lost, so a real payment error
            // (invalid_plan / payment_provider_unavailable) would otherwise be
            // misreported as "no internet". Recover the intent from the server
            // message marker before defaulting to NETWORK (the genuine transport
            // failure case).
            Result.Error(recoverPaymentError(e.message, fallback = PaymentError.NETWORK))
        }
    }

    private fun mapFunctionsError(e: FirebaseFunctionsException): PaymentError =
        when (e.code) {
            FunctionsExceptionCode.UNAUTHENTICATED -> PaymentError.UNAUTHENTICATED
            FunctionsExceptionCode.FAILED_PRECONDITION -> PaymentError.MISSING_EMAIL
            FunctionsExceptionCode.INVALID_ARGUMENT -> PaymentError.INVALID_PLAN
            FunctionsExceptionCode.UNAVAILABLE -> PaymentError.PROVIDER_UNAVAILABLE
            else -> recoverPaymentError(e.message, fallback = PaymentError.UNKNOWN)
        }
}

// Server message markers from functions/src/billing/paystackBilling.ts. Kept in
// sync there — they are how the iOS GitLive wrapper recovers the server error's
// intent when the canonical FunctionsExceptionCode is lost.
private const val MARKER_INVALID_PLAN = "invalid_plan"
private const val MARKER_PROVIDER_UNAVAILABLE = "payment_provider_unavailable"
private const val MARKER_MISSING_EMAIL = "missing_email"

internal fun recoverPaymentError(message: String?, fallback: PaymentError): PaymentError = when {
    message == null -> fallback
    message.contains(MARKER_INVALID_PLAN) -> PaymentError.INVALID_PLAN
    message.contains(MARKER_PROVIDER_UNAVAILABLE) -> PaymentError.PROVIDER_UNAVAILABLE
    message.contains(MARKER_MISSING_EMAIL) -> PaymentError.MISSING_EMAIL
    else -> fallback
}

@Serializable
private data class InitializeCheckoutRequestDto(
    val tier: String,
    val cadence: String,
)

@Serializable
private data class InitializeCheckoutResponseDto(
    val authorizationUrl: String,
    val reference: String,
)
