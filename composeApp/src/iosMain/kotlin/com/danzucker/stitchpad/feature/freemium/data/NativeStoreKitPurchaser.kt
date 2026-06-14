package com.danzucker.stitchpad.feature.freemium.data

import com.danzucker.stitchpad.core.domain.error.Error
import com.danzucker.stitchpad.core.domain.error.Result

/**
 * Platform glue implemented in Swift (see iosApp/iosApp/StoreKitPurchaserIos.swift)
 * and registered into Koin from iosApp's AppDelegate at startup. StoreKit 2's
 * `Product.purchase`, `AppStore.sync`, and `Transaction.*` APIs are Swift-only,
 * so this protocol mirrors the NativeAppleSignInLauncher pattern: suspend methods
 * returning the shared [Result] type, with the Swift side bridging async/throws
 * via `withCheckedContinuation`.
 *
 * The server re-verifies every transaction (verifyAppleTransaction), so the
 * [StoreKitPurchase.signedTransactionJws] is the only purchase field that matters
 * for entitlement — the rest is for finishing and logging. Never trust the client.
 */
interface NativeStoreKitPurchaser {
    /** Localized products for the given ids (for the displayed price). */
    suspend fun fetchProducts(productIds: List<String>): Result<List<StoreKitProduct>, StoreKitError>

    /**
     * Presents the native purchase sheet for [productId], tagging the transaction
     * with an appAccountToken derived from [accountUid] (Swift computes
     * SHA-256(uid) → UUID) so the server can bind the grant to this Firebase user.
     * Does NOT finish the transaction — the caller finishes it only after the
     * server grants (see [finishTransaction]).
     */
    suspend fun purchase(productId: String, accountUid: String): Result<StoreKitPurchase, StoreKitError>

    /** Restores active App Store entitlements (AppStore.sync + currentEntitlements). */
    suspend fun restore(): Result<List<StoreKitPurchase>, StoreKitError>

    /** Marks a StoreKit transaction finished once the server has granted it. */
    suspend fun finishTransaction(transactionId: String)
}

data class StoreKitProduct(
    val id: String,
    val displayName: String,
    val displayPrice: String,
)

data class StoreKitPurchase(
    val productId: String,
    val signedTransactionJws: String,
    val originalTransactionId: String,
    val transactionId: String,
)

enum class StoreKitError : Error {
    CANCELLED,
    PENDING,
    NETWORK,
    PRODUCT_NOT_FOUND,
    VERIFICATION_FAILED,
    UNKNOWN,
}
