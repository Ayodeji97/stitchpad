package com.danzucker.stitchpad.feature.freemium.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.freemium.domain.BillingCadence
import com.danzucker.stitchpad.feature.freemium.domain.CheckoutOutcome
import com.danzucker.stitchpad.feature.freemium.domain.PaymentError
import com.danzucker.stitchpad.feature.freemium.domain.PaymentRepository
import com.danzucker.stitchpad.feature.freemium.domain.StoreProductIds

private const val TAG = "PlayBillingRepo"

/**
 * Android PaymentRepository for Google Play Billing (every wave-1 market except
 * Nigeria; [RoutingPaymentRepository] picks it). The Play sheet buys; every
 * purchase token then goes to `verifyPlayPurchase`, which re-fetches the
 * subscription from Google, writes the tier and acknowledges. The client never
 * acknowledges and never trusts its own purchase state for entitlement.
 */
internal class PlayBillingPaymentRepository(
    private val client: NativePlayBillingClient,
    private val verifier: PlayPurchaseVerifier,
    private val authRepository: AuthRepository,
) : PaymentRepository {

    @Suppress("ReturnCount") // fast exits: bad plan, signed out, owned-list failure, same plan owned.
    override suspend fun startCheckout(
        tier: SubscriptionTier,
        cadence: BillingCadence,
    ): Result<CheckoutOutcome, PaymentError> {
        val productId = StoreProductIds.idFor(tier, cadence) ?: return Result.Error(PaymentError.INVALID_PLAN)
        val uid = authRepository.getCurrentUser()?.id ?: return Result.Error(PaymentError.UNAUTHENTICATED)

        val owned = when (val res = client.queryActivePurchases()) {
            is Result.Success -> res.data.filter { it.state == PlayPurchaseState.PURCHASED && it.isOursFor(uid) }
            // Without the owned list we cannot pass the old token, and buying
            // anyway could stack a second subscription (double charge).
            is Result.Error -> return Result.Error(res.error.toPaymentError())
        }
        // Same plan already owned: bind/refresh it server-side instead of buying.
        owned.firstOrNull { productId in it.productIds }?.let { return verifyToOutcome(it.purchaseToken) }
        // A different plan is owned: replace it (tier or cadence change), never stack.
        val oldToken = owned.firstOrNull()?.purchaseToken

        return when (val res = client.purchase(productId, sha256Hex(uid), oldToken)) {
            is Result.Success -> onPurchased(res.data)
            is Result.Error -> when (res.error) {
                PlayBillingError.CANCELLED -> Result.Success(CheckoutOutcome.Cancelled)
                PlayBillingError.ALREADY_OWNED -> reverifyOwned(productId, uid)
                else -> Result.Error(res.error.toPaymentError())
            }
        }
    }

    override suspend fun productCatalog(): Result<Map<String, String>, PaymentError> =
        when (val res = client.fetchProducts(StoreProductIds.ALL)) {
            is Result.Success -> Result.Success(res.data.associate { it.id to it.formattedPrice })
            is Result.Error -> Result.Error(res.error.toPaymentError())
        }

    @Suppress("ReturnCount") // signed out, query failure
    override suspend fun restorePurchases(): Result<CheckoutOutcome, PaymentError> {
        val uid = authRepository.getCurrentUser()?.id ?: return Result.Error(PaymentError.UNAUTHENTICATED)
        val purchases = when (val res = client.queryActivePurchases()) {
            is Result.Success -> res.data.filter { it.state == PlayPurchaseState.PURCHASED && it.isOursFor(uid) }
            is Result.Error -> return Result.Error(res.error.toPaymentError())
        }
        val statuses = mutableListOf<PlayVerifyStatus>()
        var lastError: PaymentError? = null
        for (purchase in purchases) {
            when (val verified = verifier.verify(purchase.purchaseToken)) {
                is Result.Success -> statuses += verified.data
                is Result.Error -> lastError = verified.error
            }
        }
        return when {
            PlayVerifyStatus.ACTIVE in statuses -> Result.Success(CheckoutOutcome.PurchasedAndGranted)
            PlayVerifyStatus.BLOCKED in statuses -> Result.Success(CheckoutOutcome.Deferred)
            lastError != null -> Result.Error(lastError)
            else -> Result.Success(CheckoutOutcome.Cancelled)
        }
    }

    /**
     * Sends every PURCHASED but not yet acknowledged token to the server: pending
     * cash/carrier payments that cleared while the app was closed, and purchases
     * whose first verify failed. The server acknowledges after granting, which
     * also stops Google's 3-day auto-refund. Used by PlayPurchaseSweeper.
     */
    suspend fun verifyUnacknowledged() {
        val uid = authRepository.getCurrentUser()?.id ?: return
        val purchases = (client.queryActivePurchases() as? Result.Success)?.data ?: return
        purchases
            .filter { it.state == PlayPurchaseState.PURCHASED && !it.isAcknowledged && it.isOursFor(uid) }
            .forEach { purchase ->
                val result = verifier.verify(purchase.purchaseToken)
                if (result is Result.Error) {
                    AppLogger.w(tag = TAG) { "sweep verify failed: ${result.error}" }
                }
            }
    }

    private suspend fun onPurchased(purchase: PlayPurchase): Result<CheckoutOutcome, PaymentError> {
        if (purchase.state == PlayPurchaseState.PENDING) {
            // Bind the token server-side now (no grant for a pending purchase) so the
            // RTDN can grant when the payment clears even if the app stays closed.
            // Best effort: the sweep re-verifies on the next app open anyway.
            verifier.verify(purchase.purchaseToken)
            return Result.Success(CheckoutOutcome.Pending)
        }
        return verifyToOutcome(purchase.purchaseToken)
    }

    private suspend fun reverifyOwned(productId: String, uid: String): Result<CheckoutOutcome, PaymentError> {
        val owned = (client.queryActivePurchases() as? Result.Success)?.data
            ?.firstOrNull { productId in it.productIds && it.state == PlayPurchaseState.PURCHASED && it.isOursFor(uid) }
            ?: return Result.Error(PaymentError.UNKNOWN)
        return verifyToOutcome(owned.purchaseToken)
    }

    private suspend fun verifyToOutcome(purchaseToken: String): Result<CheckoutOutcome, PaymentError> =
        when (val verified = verifier.verify(purchaseToken)) {
            is Result.Success -> when (verified.data) {
                PlayVerifyStatus.ACTIVE -> Result.Success(CheckoutOutcome.PurchasedAndGranted)
                PlayVerifyStatus.BLOCKED -> Result.Success(CheckoutOutcome.Deferred)
                PlayVerifyStatus.PENDING -> Result.Success(CheckoutOutcome.Pending)
                PlayVerifyStatus.EXPIRED -> Result.Error(PaymentError.UNKNOWN)
            }
            is Result.Error -> Result.Error(verified.error)
        }

    /** One of our subscription products, bought by THIS StitchPad account (not another on the device). */
    private fun PlayPurchase.isOursFor(uid: String): Boolean =
        productIds.any { it in StoreProductIds.ALL } && obfuscatedAccountId == sha256Hex(uid)

    private fun PlayBillingError.toPaymentError(): PaymentError = when (this) {
        PlayBillingError.NETWORK -> PaymentError.NETWORK
        PlayBillingError.BILLING_UNAVAILABLE -> PaymentError.PROVIDER_UNAVAILABLE
        PlayBillingError.PRODUCT_NOT_FOUND -> PaymentError.INVALID_PLAN
        PlayBillingError.CANCELLED,
        PlayBillingError.ALREADY_OWNED,
        PlayBillingError.UNKNOWN -> PaymentError.UNKNOWN
    }
}
