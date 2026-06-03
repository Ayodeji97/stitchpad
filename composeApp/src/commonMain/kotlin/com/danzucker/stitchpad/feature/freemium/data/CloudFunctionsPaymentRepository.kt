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
            Result.Error(PaymentError.NETWORK)
        }
    }

    private fun mapFunctionsError(e: FirebaseFunctionsException): PaymentError =
        when (e.code) {
            FunctionsExceptionCode.UNAUTHENTICATED -> PaymentError.UNAUTHENTICATED
            FunctionsExceptionCode.INVALID_ARGUMENT -> PaymentError.INVALID_PLAN
            FunctionsExceptionCode.UNAVAILABLE -> PaymentError.PROVIDER_UNAVAILABLE
            else -> PaymentError.UNKNOWN
        }
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
