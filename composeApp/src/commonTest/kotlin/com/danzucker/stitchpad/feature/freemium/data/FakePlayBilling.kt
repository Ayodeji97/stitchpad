package com.danzucker.stitchpad.feature.freemium.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.freemium.domain.PaymentError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeNativePlayBillingClient : NativePlayBillingClient {
    var products: Result<List<PlayProduct>, PlayBillingError> = Result.Success(emptyList())
    var activePurchases: Result<List<PlayPurchase>, PlayBillingError> = Result.Success(emptyList())
    var purchaseResult: Result<PlayPurchase, PlayBillingError> = Result.Error(PlayBillingError.UNKNOWN)

    data class PurchaseCall(val productId: String, val obfuscatedAccountId: String, val oldPurchaseToken: String?)
    val purchaseCalls = mutableListOf<PurchaseCall>()
    val fetchCalls = mutableListOf<List<String>>()

    val updates = MutableSharedFlow<PlayPurchase>(extraBufferCapacity = 8)
    override val purchaseUpdates: Flow<PlayPurchase> = updates

    override suspend fun fetchProducts(productIds: List<String>): Result<List<PlayProduct>, PlayBillingError> {
        fetchCalls += productIds
        return products
    }

    override suspend fun purchase(
        productId: String,
        obfuscatedAccountId: String,
        oldPurchaseToken: String?,
    ): Result<PlayPurchase, PlayBillingError> {
        purchaseCalls += PurchaseCall(productId, obfuscatedAccountId, oldPurchaseToken)
        return purchaseResult
    }

    override suspend fun queryActivePurchases(): Result<List<PlayPurchase>, PlayBillingError> = activePurchases
}

class FakePlayPurchaseVerifier : PlayPurchaseVerifier {
    var result: Result<PlayVerifyStatus, PaymentError> = Result.Success(PlayVerifyStatus.ACTIVE)
    val verifiedTokens = mutableListOf<String>()

    override suspend fun verify(purchaseToken: String): Result<PlayVerifyStatus, PaymentError> {
        verifiedTokens += purchaseToken
        return result
    }
}
