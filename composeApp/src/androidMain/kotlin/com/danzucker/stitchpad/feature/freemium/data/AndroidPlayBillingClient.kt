package com.danzucker.stitchpad.feature.freemium.data

import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingFlowParams.SubscriptionUpdateParams.ReplacementMode
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.auth.data.CurrentActivityHolder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private const val TAG = "PlayBillingClient"

/**
 * [NativePlayBillingClient] over the Play Billing Library. Connects lazily on the
 * first call; the library's auto service reconnection handles a dropped
 * service. Never acknowledges: verifyPlayPurchase acknowledges server-side after
 * the grant commits.
 */
internal class AndroidPlayBillingClient(
    context: Context,
    private val activityHolder: CurrentActivityHolder,
) : NativePlayBillingClient {

    private val updates = MutableSharedFlow<PlayPurchase>(extraBufferCapacity = 16)
    override val purchaseUpdates: Flow<PlayPurchase> = updates.asSharedFlow()

    // The in-flight launchBillingFlow, completed by the PurchasesUpdatedListener.
    @Volatile private var inFlight: CompletableDeferred<Result<PlayPurchase, PlayBillingError>>? = null
    private val connectMutex = Mutex()

    private val listener = PurchasesUpdatedListener { result, purchases ->
        val deferred = inFlight
        inFlight = null
        val mapped = purchases.orEmpty().mapNotNull { it.toPlayPurchase() }
        if (deferred == null) {
            // Out-of-flow update: a pending payment cleared, or a renewal. The sweep verifies it.
            mapped.filter { it.state == PlayPurchaseState.PURCHASED }.forEach { updates.tryEmit(it) }
            return@PurchasesUpdatedListener
        }
        if (result.responseCode == BillingResponseCode.OK && mapped.isNotEmpty()) {
            deferred.complete(Result.Success(mapped.first()))
        } else {
            deferred.complete(Result.Error(result.toPlayBillingError()))
        }
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(listener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    override suspend fun fetchProducts(productIds: List<String>): Result<List<PlayProduct>, PlayBillingError> =
        when (val res = queryDetails(productIds)) {
            is Result.Success -> Result.Success(
                res.data.mapNotNull { details ->
                    details.basePlanOffer()?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
                        ?.let { PlayProduct(id = details.productId, formattedPrice = it) }
                },
            )
            is Result.Error -> res
        }

    @Suppress("ReturnCount") // each Play precondition (details, offer, activity, launch) fails fast.
    override suspend fun purchase(
        productId: String,
        obfuscatedAccountId: String,
        oldPurchaseToken: String?,
    ): Result<PlayPurchase, PlayBillingError> {
        val details = when (val res = queryDetails(listOf(productId))) {
            is Result.Success -> res.data.firstOrNull() ?: return Result.Error(PlayBillingError.PRODUCT_NOT_FOUND)
            is Result.Error -> return res
        }
        val offerToken = details.basePlanOffer()?.offerToken ?: return Result.Error(PlayBillingError.PRODUCT_NOT_FOUND)
        val activity = activityHolder.activity ?: return Result.Error(PlayBillingError.UNKNOWN)

        val paramsBuilder = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build(),
                ),
            )
            .setObfuscatedAccountId(obfuscatedAccountId)
        if (oldPurchaseToken != null) {
            paramsBuilder.setSubscriptionUpdateParams(
                BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                    .setOldPurchaseToken(oldPurchaseToken)
                    .setSubscriptionReplacementMode(ReplacementMode.WITH_TIME_PRORATION)
                    .build(),
            )
        }

        val deferred = CompletableDeferred<Result<PlayPurchase, PlayBillingError>>()
        inFlight = deferred
        val launch = withContext(Dispatchers.Main) { billingClient.launchBillingFlow(activity, paramsBuilder.build()) }
        if (launch.responseCode != BillingResponseCode.OK) {
            inFlight = null
            return Result.Error(launch.toPlayBillingError())
        }
        return deferred.await()
    }

    override suspend fun queryActivePurchases(): Result<List<PlayPurchase>, PlayBillingError> {
        if (!ensureConnected()) return Result.Error(PlayBillingError.BILLING_UNAVAILABLE)
        val res = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        )
        return if (res.billingResult.responseCode == BillingResponseCode.OK) {
            Result.Success(res.purchasesList.mapNotNull { it.toPlayPurchase() })
        } else {
            Result.Error(res.billingResult.toPlayBillingError())
        }
    }

    private suspend fun queryDetails(productIds: List<String>): Result<List<ProductDetails>, PlayBillingError> {
        if (!ensureConnected()) return Result.Error(PlayBillingError.BILLING_UNAVAILABLE)
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                productIds.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                },
            )
            .build()
        val res = billingClient.queryProductDetails(params)
        return if (res.billingResult.responseCode == BillingResponseCode.OK) {
            Result.Success(res.productDetailsList.orEmpty())
        } else {
            Result.Error(res.billingResult.toPlayBillingError())
        }
    }

    private suspend fun ensureConnected(): Boolean = connectMutex.withLock {
        if (billingClient.isReady) return@withLock true
        suspendCancellableCoroutine { cont ->
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (!cont.isActive) return
                    if (result.responseCode != BillingResponseCode.OK) {
                        AppLogger.w(tag = TAG) { "billing setup failed: ${result.responseCode} ${result.debugMessage}" }
                    }
                    cont.resume(result.responseCode == BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    // Auto service reconnection re-establishes the next call's connection.
                    if (cont.isActive) cont.resume(false)
                }
            })
        }
    }

    // One base plan per product (monthly or annual); prefer the base plan itself
    // (no offerId) over any promotional offer.
    private fun ProductDetails.basePlanOffer(): ProductDetails.SubscriptionOfferDetails? {
        val offers = subscriptionOfferDetails.orEmpty()
        return offers.firstOrNull { it.offerId == null } ?: offers.firstOrNull()
    }

    private fun Purchase.toPlayPurchase(): PlayPurchase? {
        val state = when (purchaseState) {
            Purchase.PurchaseState.PURCHASED -> PlayPurchaseState.PURCHASED
            Purchase.PurchaseState.PENDING -> PlayPurchaseState.PENDING
            else -> return null
        }
        return PlayPurchase(
            productIds = products,
            purchaseToken = purchaseToken,
            state = state,
            isAcknowledged = isAcknowledged,
        )
    }

    private fun BillingResult.toPlayBillingError(): PlayBillingError = when (responseCode) {
        BillingResponseCode.USER_CANCELED -> PlayBillingError.CANCELLED
        BillingResponseCode.NETWORK_ERROR,
        BillingResponseCode.SERVICE_TIMEOUT,
        BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingResponseCode.SERVICE_DISCONNECTED -> PlayBillingError.NETWORK
        BillingResponseCode.BILLING_UNAVAILABLE,
        BillingResponseCode.FEATURE_NOT_SUPPORTED -> PlayBillingError.BILLING_UNAVAILABLE
        BillingResponseCode.ITEM_UNAVAILABLE -> PlayBillingError.PRODUCT_NOT_FOUND
        BillingResponseCode.ITEM_ALREADY_OWNED -> PlayBillingError.ALREADY_OWNED
        else -> PlayBillingError.UNKNOWN
    }
}
