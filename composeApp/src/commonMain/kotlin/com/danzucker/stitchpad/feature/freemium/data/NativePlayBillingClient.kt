package com.danzucker.stitchpad.feature.freemium.data

import com.danzucker.stitchpad.core.domain.error.Error
import com.danzucker.stitchpad.core.domain.error.Result
import kotlinx.coroutines.flow.Flow

/**
 * Google Play Billing seam. Declared in commonMain (no Billing Library types) so
 * [PlayBillingPaymentRepository] and the purchase sweep are unit tested in
 * commonTest; the real implementation is AndroidPlayBillingClient (androidMain).
 */
interface NativePlayBillingClient {
    suspend fun fetchProducts(productIds: List<String>): Result<List<PlayProduct>, PlayBillingError>

    /** Launches the purchase sheet; obfuscatedAccountId = sha256Hex(uid). oldPurchaseToken set → replacement. */
    suspend fun purchase(
        productId: String,
        obfuscatedAccountId: String,
        oldPurchaseToken: String?,
    ): Result<PlayPurchase, PlayBillingError>

    suspend fun queryActivePurchases(): Result<List<PlayPurchase>, PlayBillingError>

    /** Purchases that reach PURCHASED while the app runs (pending completions, renewals). */
    val purchaseUpdates: Flow<PlayPurchase>
}

data class PlayProduct(val id: String, val formattedPrice: String)

data class PlayPurchase(
    val productIds: List<String>,
    val purchaseToken: String,
    val state: PlayPurchaseState,
    val isAcknowledged: Boolean,
)

enum class PlayPurchaseState { PURCHASED, PENDING }

enum class PlayBillingError : Error {
    CANCELLED,
    NETWORK,
    BILLING_UNAVAILABLE,
    PRODUCT_NOT_FOUND,
    ALREADY_OWNED,
    UNKNOWN,
}
