package com.danzucker.stitchpad.feature.freemium.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.freemium.domain.BillingCadence
import com.danzucker.stitchpad.feature.freemium.domain.CheckoutOutcome
import com.danzucker.stitchpad.feature.freemium.domain.PaymentError
import com.danzucker.stitchpad.feature.freemium.domain.StoreProductIds
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayBillingPaymentRepositoryTest {

    private val client = FakeNativePlayBillingClient()
    private val verifier = FakePlayPurchaseVerifier()
    private val auth = FakeAuthRepository().apply {
        currentUser = User(
            id = "uid-1",
            email = "ama@example.com",
            displayName = "Ama",
            businessName = null,
            phoneNumber = null,
            whatsappNumber = null,
            avatarColorIndex = 0,
        )
    }
    private val repo = PlayBillingPaymentRepository(client = client, verifier = verifier, authRepository = auth)

    private val myAccountId = sha256Hex("uid-1")
    private val otherAccountId = sha256Hex("someone-else")

    private fun purchased(
        productId: String,
        token: String,
        acknowledged: Boolean = false,
        accountId: String? = myAccountId,
    ) = PlayPurchase(
        productIds = listOf(productId),
        purchaseToken = token,
        state = PlayPurchaseState.PURCHASED,
        isAcknowledged = acknowledged,
        obfuscatedAccountId = accountId,
    )

    @Test
    fun purchase_verifiesTokenAndReturnsGranted() = runTest {
        client.purchaseResult = Result.Success(purchased(StoreProductIds.PRO_MONTHLY, "tok-1"))

        val result = repo.startCheckout(SubscriptionTier.PRO, BillingCadence.MONTHLY)

        assertEquals(Result.Success(CheckoutOutcome.PurchasedAndGranted), result)
        assertEquals(
            FakeNativePlayBillingClient.PurchaseCall(StoreProductIds.PRO_MONTHLY, sha256Hex("uid-1"), null),
            client.purchaseCalls.single(),
        )
        assertEquals(listOf("tok-1"), verifier.verifiedTokens)
    }

    @Test
    fun cancelled_returnsCancelledOutcome() = runTest {
        client.purchaseResult = Result.Error(PlayBillingError.CANCELLED)
        assertEquals(
            Result.Success(CheckoutOutcome.Cancelled),
            repo.startCheckout(SubscriptionTier.PRO, BillingCadence.MONTHLY),
        )
        assertTrue(verifier.verifiedTokens.isEmpty())
    }

    @Test
    fun pendingPurchase_returnsPendingOutcome() = runTest {
        client.purchaseResult = Result.Success(
            purchased(StoreProductIds.PRO_MONTHLY, "tok-p").copy(state = PlayPurchaseState.PENDING),
        )
        verifier.result = Result.Success(PlayVerifyStatus.PENDING)

        assertEquals(
            Result.Success(CheckoutOutcome.Pending),
            repo.startCheckout(SubscriptionTier.PRO, BillingCadence.MONTHLY),
        )
        // The pending token is sent once so the server binds it (index only, no
        // grant); that lets the RTDN grant access when the cash payment clears
        // even if the app stays closed.
        assertEquals(listOf("tok-p"), verifier.verifiedTokens)
    }

    @Test
    fun pendingPurchase_bindFailure_stillReturnsPending() = runTest {
        client.purchaseResult = Result.Success(
            purchased(StoreProductIds.PRO_MONTHLY, "tok-p").copy(state = PlayPurchaseState.PENDING),
        )
        verifier.result = Result.Error(PaymentError.NETWORK)
        assertEquals(
            Result.Success(CheckoutOutcome.Pending),
            repo.startCheckout(SubscriptionTier.PRO, BillingCadence.MONTHLY),
        )
    }

    @Test
    fun upgrade_passesOldTokenForReplacement() = runTest {
        client.activePurchases = Result.Success(listOf(purchased(StoreProductIds.PRO_MONTHLY, "tok-pro", true)))
        client.purchaseResult = Result.Success(purchased(StoreProductIds.ATELIER_MONTHLY, "tok-atelier"))

        val result = repo.startCheckout(SubscriptionTier.ATELIER, BillingCadence.MONTHLY)

        assertEquals(Result.Success(CheckoutOutcome.PurchasedAndGranted), result)
        assertEquals("tok-pro", client.purchaseCalls.single().oldPurchaseToken)
        assertEquals(StoreProductIds.ATELIER_MONTHLY, client.purchaseCalls.single().productId)
        assertEquals(listOf("tok-atelier"), verifier.verifiedTokens)
    }

    @Test
    fun samePlanOwned_verifiesInsteadOfBuying() = runTest {
        client.activePurchases = Result.Success(listOf(purchased(StoreProductIds.PRO_MONTHLY, "tok-owned", true)))

        val result = repo.startCheckout(SubscriptionTier.PRO, BillingCadence.MONTHLY)

        assertEquals(Result.Success(CheckoutOutcome.PurchasedAndGranted), result)
        assertTrue(client.purchaseCalls.isEmpty())
        assertEquals(listOf("tok-owned"), verifier.verifiedTokens)
    }

    @Test
    fun alreadyOwnedFromBillingFlow_reverifiesOwnedToken() = runTest {
        // Play reports ITEM_ALREADY_OWNED although the pre-query missed it (stale cache).
        client.purchaseResult = Result.Error(PlayBillingError.ALREADY_OWNED)
        var queries = 0
        val owned = purchased(StoreProductIds.PRO_MONTHLY, "tok-owned", true)
        val lateClient = object : NativePlayBillingClient by client {
            override suspend fun queryActivePurchases(): Result<List<PlayPurchase>, PlayBillingError> {
                queries++
                return if (queries == 1) Result.Success(emptyList()) else Result.Success(listOf(owned))
            }
        }
        val lateRepo = PlayBillingPaymentRepository(lateClient, verifier, auth)

        val result = lateRepo.startCheckout(SubscriptionTier.PRO, BillingCadence.MONTHLY)

        assertEquals(Result.Success(CheckoutOutcome.PurchasedAndGranted), result)
        assertEquals(listOf("tok-owned"), verifier.verifiedTokens)
    }

    @Test
    fun verifyFailure_surfacesPaymentError() = runTest {
        client.purchaseResult = Result.Success(purchased(StoreProductIds.PRO_MONTHLY, "tok-1"))
        verifier.result = Result.Error(PaymentError.PROVIDER_UNAVAILABLE)
        assertEquals(
            Result.Error(PaymentError.PROVIDER_UNAVAILABLE),
            repo.startCheckout(SubscriptionTier.PRO, BillingCadence.MONTHLY),
        )
    }

    @Test
    fun verifyFailure_mapsViaRecoverPaymentError() {
        assertEquals(
            PaymentError.UNKNOWN,
            playVerifyError(CallableErrorKind.OTHER, "account_mismatch"),
        )
        assertEquals(
            PaymentError.PROVIDER_UNAVAILABLE,
            playVerifyError(CallableErrorKind.UNAVAILABLE, "play_unavailable"),
        )
        assertEquals(PaymentError.PROVIDER_UNAVAILABLE, playVerifyError(null, "play_unavailable"))
        assertEquals(PaymentError.UNAUTHENTICATED, playVerifyError(CallableErrorKind.UNAUTHENTICATED, null))
    }

    @Test
    fun billingErrors_mapToPaymentErrors() = runTest {
        client.purchaseResult = Result.Error(PlayBillingError.BILLING_UNAVAILABLE)
        assertEquals(
            Result.Error(PaymentError.PROVIDER_UNAVAILABLE),
            repo.startCheckout(SubscriptionTier.PRO, BillingCadence.MONTHLY),
        )
        client.purchaseResult = Result.Error(PlayBillingError.NETWORK)
        assertEquals(
            Result.Error(PaymentError.NETWORK),
            repo.startCheckout(SubscriptionTier.PRO, BillingCadence.MONTHLY),
        )
        client.purchaseResult = Result.Error(PlayBillingError.PRODUCT_NOT_FOUND)
        assertEquals(
            Result.Error(PaymentError.INVALID_PLAN),
            repo.startCheckout(SubscriptionTier.PRO, BillingCadence.MONTHLY),
        )
    }

    @Test
    fun signedOut_returnsUnauthenticated() = runTest {
        auth.currentUser = null
        assertEquals(
            Result.Error(PaymentError.UNAUTHENTICATED),
            repo.startCheckout(SubscriptionTier.PRO, BillingCadence.MONTHLY),
        )
        assertTrue(client.purchaseCalls.isEmpty())
    }

    @Test
    fun freeTier_isInvalidPlan() = runTest {
        assertEquals(
            Result.Error(PaymentError.INVALID_PLAN),
            repo.startCheckout(SubscriptionTier.FREE, BillingCadence.MONTHLY),
        )
    }

    @Test
    fun catalog_mapsFormattedPrices() = runTest {
        client.products = Result.Success(
            listOf(
                PlayProduct(StoreProductIds.PRO_MONTHLY, "GH₵ 15.00"),
                PlayProduct(StoreProductIds.ATELIER_ANNUAL, "GH₵ 300.00"),
            ),
        )
        assertEquals(
            Result.Success(
                mapOf(StoreProductIds.PRO_MONTHLY to "GH₵ 15.00", StoreProductIds.ATELIER_ANNUAL to "GH₵ 300.00"),
            ),
            repo.productCatalog(),
        )
        assertEquals(StoreProductIds.ALL, client.fetchCalls.single())
    }

    @Test
    fun restore_verifiesEachActivePurchase() = runTest {
        client.activePurchases = Result.Success(
            listOf(
                purchased(StoreProductIds.PRO_MONTHLY, "tok-a", true),
                purchased(StoreProductIds.ATELIER_ANNUAL, "tok-b", true),
            ),
        )
        assertEquals(Result.Success(CheckoutOutcome.PurchasedAndGranted), repo.restorePurchases())
        assertEquals(listOf("tok-a", "tok-b"), verifier.verifiedTokens)
    }

    @Test
    fun restore_none_isCancelled() = runTest {
        assertEquals(Result.Success(CheckoutOutcome.Cancelled), repo.restorePurchases())
        assertTrue(verifier.verifiedTokens.isEmpty())
    }

    @Test
    fun restore_allFail_surfacesError() = runTest {
        client.activePurchases = Result.Success(listOf(purchased(StoreProductIds.PRO_MONTHLY, "tok-a", true)))
        verifier.result = Result.Error(PaymentError.NETWORK)
        assertEquals(Result.Error(PaymentError.NETWORK), repo.restorePurchases())
    }

    @Test
    fun verifyUnacknowledged_onlyPurchasedAndUnacknowledged() = runTest {
        client.activePurchases = Result.Success(
            listOf(
                purchased(StoreProductIds.PRO_MONTHLY, "tok-new", acknowledged = false),
                purchased(StoreProductIds.PRO_ANNUAL, "tok-acked", acknowledged = true),
                purchased(StoreProductIds.ATELIER_MONTHLY, "tok-pending").copy(state = PlayPurchaseState.PENDING),
            ),
        )
        repo.verifyUnacknowledged()
        assertEquals(listOf("tok-new"), verifier.verifiedTokens)
    }

    @Test
    fun otherAccountsPurchase_isNotReplaced() = runTest {
        // Another StitchPad account bought Pro on this device's Google account.
        client.activePurchases = Result.Success(
            listOf(purchased(StoreProductIds.PRO_MONTHLY, "tok-theirs", true, accountId = otherAccountId)),
        )
        client.purchaseResult = Result.Success(purchased(StoreProductIds.ATELIER_MONTHLY, "tok-mine"))

        repo.startCheckout(SubscriptionTier.ATELIER, BillingCadence.MONTHLY)

        assertEquals(null, client.purchaseCalls.single().oldPurchaseToken)
        assertEquals(listOf("tok-mine"), verifier.verifiedTokens)
    }

    @Test
    fun otherAccountsSamePlan_isNotVerifiedAsMine() = runTest {
        client.activePurchases = Result.Success(
            listOf(purchased(StoreProductIds.PRO_MONTHLY, "tok-theirs", true, accountId = otherAccountId)),
        )
        client.purchaseResult = Result.Error(PlayBillingError.ALREADY_OWNED)

        val result = repo.startCheckout(SubscriptionTier.PRO, BillingCadence.MONTHLY)

        assertEquals(Result.Error(PaymentError.UNKNOWN), result)
        assertTrue(verifier.verifiedTokens.isEmpty())
    }

    @Test
    fun restore_ignoresOtherAccountsPurchases() = runTest {
        client.activePurchases = Result.Success(
            listOf(
                purchased(StoreProductIds.PRO_MONTHLY, "tok-theirs", true, accountId = otherAccountId),
                purchased(StoreProductIds.ATELIER_MONTHLY, "tok-noaccount", true, accountId = null),
            ),
        )
        assertEquals(Result.Success(CheckoutOutcome.Cancelled), repo.restorePurchases())
        assertTrue(verifier.verifiedTokens.isEmpty())
    }

    @Test
    fun verifyUnacknowledged_ignoresOtherAccountsPurchases() = runTest {
        client.activePurchases = Result.Success(
            listOf(
                purchased(StoreProductIds.PRO_MONTHLY, "tok-theirs", accountId = otherAccountId),
                purchased(StoreProductIds.PRO_ANNUAL, "tok-mine"),
            ),
        )
        repo.verifyUnacknowledged()
        assertEquals(listOf("tok-mine"), verifier.verifiedTokens)
    }

    @Test
    fun blockedByActivePlan_returnsDeferred() = runTest {
        client.purchaseResult = Result.Success(purchased(StoreProductIds.PRO_MONTHLY, "tok-1"))
        verifier.result = Result.Success(PlayVerifyStatus.BLOCKED)
        assertEquals(
            Result.Success(CheckoutOutcome.Deferred),
            repo.startCheckout(SubscriptionTier.PRO, BillingCadence.MONTHLY),
        )
    }

    @Test
    fun restore_onlyBlocked_returnsDeferred() = runTest {
        client.activePurchases = Result.Success(listOf(purchased(StoreProductIds.PRO_MONTHLY, "tok-a", true)))
        verifier.result = Result.Success(PlayVerifyStatus.BLOCKED)
        assertEquals(Result.Success(CheckoutOutcome.Deferred), repo.restorePurchases())
    }

    @Test
    fun verifyResponse_mapsBlockedFlag() {
        assertEquals(PlayVerifyStatus.ACTIVE, playVerifyStatus(status = "active", blocked = false))
        assertEquals(PlayVerifyStatus.BLOCKED, playVerifyStatus(status = "active", blocked = true))
        assertEquals(PlayVerifyStatus.PENDING, playVerifyStatus(status = "pending", blocked = false))
        assertEquals(PlayVerifyStatus.EXPIRED, playVerifyStatus(status = "expired", blocked = false))
    }

    @Test
    fun sha256Hex_matchesKnownVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex("abc"))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256Hex(""))
        // Multi-block input (> 55 bytes) and non-ASCII (UTF-8) input.
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            sha256Hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
        )
    }
}
