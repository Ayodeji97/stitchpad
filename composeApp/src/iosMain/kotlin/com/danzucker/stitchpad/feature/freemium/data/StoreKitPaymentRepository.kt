package com.danzucker.stitchpad.feature.freemium.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.freemium.domain.AppleProductIds
import com.danzucker.stitchpad.feature.freemium.domain.BillingCadence
import com.danzucker.stitchpad.feature.freemium.domain.CheckoutOutcome
import com.danzucker.stitchpad.feature.freemium.domain.PaymentError
import com.danzucker.stitchpad.feature.freemium.domain.PaymentRepository
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.FunctionsExceptionCode
import kotlinx.serialization.Serializable

private const val TAG = "StoreKitPaymentRepo"

/**
 * iOS PaymentRepository backed by Apple In-App Purchase (StoreKit 2). The native
 * [NativeStoreKitPurchaser] presents the purchase sheet and returns Apple's signed
 * transaction JWS; this repository forwards the JWS to the `verifyAppleTransaction`
 * Cloud Function, which verifies it and writes the subscription tier to Firestore
 * (the same field the Paystack webhook writes). Only after the server grants do we
 * finish the StoreKit transaction.
 */
internal class StoreKitPaymentRepository(
    private val purchaser: NativeStoreKitPurchaser,
    private val functions: FirebaseFunctions,
    private val authRepository: AuthRepository,
) : PaymentRepository {

    override suspend fun startCheckout(
        tier: SubscriptionTier,
        cadence: BillingCadence,
    ): Result<CheckoutOutcome, PaymentError> {
        val productId = AppleProductIds.idFor(tier, cadence)
        val uid = authRepository.getCurrentUser()?.id
        return when {
            productId == null -> Result.Error(PaymentError.INVALID_PLAN)
            uid == null -> Result.Error(PaymentError.UNAUTHENTICATED)
            else -> when (val purchase = purchaser.purchase(productId = productId, accountUid = uid)) {
                is Result.Success -> verifyAndFinish(purchase.data)
                is Result.Error -> when (purchase.error) {
                    // User-driven, non-error outcomes surface as outcomes, not errors.
                    StoreKitError.CANCELLED -> Result.Success(CheckoutOutcome.Cancelled)
                    StoreKitError.PENDING -> Result.Success(CheckoutOutcome.Pending)
                    else -> Result.Error(purchase.error.toPaymentError())
                }
            }
        }
    }

    override suspend fun productCatalog(): Result<Map<String, String>, PaymentError> =
        when (val res = purchaser.fetchProducts(AppleProductIds.ALL)) {
            is Result.Success -> Result.Success(res.data.associate { it.id to it.displayPrice })
            is Result.Error -> Result.Error(res.error.toPaymentError())
        }

    override suspend fun restorePurchases(): Result<CheckoutOutcome, PaymentError> =
        when (val res = purchaser.restore()) {
            is Result.Success -> {
                var grantedAny = false
                var lastError: PaymentError? = null
                for (purchase in res.data) {
                    when (val verified = verifyOnServer(purchase.signedTransactionJws)) {
                        is Result.Success -> {
                            purchaser.finishTransaction(purchase.transactionId)
                            grantedAny = true
                        }
                        is Result.Error -> lastError = verified.error
                    }
                }
                when {
                    // Success flows through the entitlements provider, which pops the screen.
                    grantedAny -> Result.Success(CheckoutOutcome.PurchasedAndGranted)
                    // Had entitlements but none could be verified (transient/server
                    // failure) — surface it so the user can retry, not a silent no-op.
                    lastError != null -> Result.Error(lastError)
                    // Genuinely nothing to restore.
                    else -> Result.Success(CheckoutOutcome.Cancelled)
                }
            }
            is Result.Error -> Result.Error(res.error.toPaymentError())
        }

    /**
     * Re-verifies a transaction surfaced by Swift's `Transaction.updates` listener
     * (Ask-to-Buy approvals, renewals, or recovery of a purchase whose first verify
     * failed). Finishes only on a successful server grant. Called from
     * [IosStoreKitBridge].
     */
    suspend fun reconcileTransaction(signedTransactionJws: String, transactionId: String) {
        if (verifyOnServer(signedTransactionJws) is Result.Success) {
            purchaser.finishTransaction(transactionId)
        }
    }

    private suspend fun verifyAndFinish(purchase: StoreKitPurchase): Result<CheckoutOutcome, PaymentError> =
        when (val verified = verifyOnServer(purchase.signedTransactionJws)) {
            is Result.Success -> {
                purchaser.finishTransaction(purchase.transactionId)
                Result.Success(CheckoutOutcome.PurchasedAndGranted)
            }
            is Result.Error -> Result.Error(verified.error)
        }

    private suspend fun verifyOnServer(signedTransactionJws: String): Result<Unit, PaymentError> {
        return try {
            functions
                .httpsCallable("verifyAppleTransaction")
                .invoke(VerifyAppleTransactionRequestDto(signedTransactionJws = signedTransactionJws))
            Result.Success(Unit)
        } catch (e: FirebaseFunctionsException) {
            AppLogger.e(tag = TAG, throwable = e) {
                "verifyAppleTransaction failed: code=${e.code} message=${e.message}"
            }
            Result.Error(mapFunctionsError(e))
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            AppLogger.e(tag = TAG, throwable = e) {
                "verifyAppleTransaction threw ${e::class.simpleName}: ${e.message}"
            }
            // GitLive on iOS can drop the canonical HttpsError code (see the same
            // recovery in CloudFunctionsPaymentRepository); recover from the marker.
            Result.Error(recoverPaymentError(e.message, fallback = PaymentError.NETWORK))
        }
    }

    private fun mapFunctionsError(e: FirebaseFunctionsException): PaymentError =
        when (e.code) {
            FunctionsExceptionCode.UNAUTHENTICATED -> PaymentError.UNAUTHENTICATED
            FunctionsExceptionCode.INVALID_ARGUMENT ->
                recoverPaymentError(e.message, fallback = PaymentError.UNKNOWN)
            else -> recoverPaymentError(e.message, fallback = PaymentError.UNKNOWN)
        }

    private fun StoreKitError.toPaymentError(): PaymentError = when (this) {
        StoreKitError.NETWORK -> PaymentError.NETWORK
        StoreKitError.PRODUCT_NOT_FOUND -> PaymentError.INVALID_PLAN
        // CANCELLED / PENDING are handled as outcomes before reaching here.
        StoreKitError.CANCELLED,
        StoreKitError.PENDING,
        StoreKitError.VERIFICATION_FAILED,
        StoreKitError.UNKNOWN -> PaymentError.UNKNOWN
    }
}

@Serializable
private data class VerifyAppleTransactionRequestDto(
    val signedTransactionJws: String,
)
