package com.danzucker.stitchpad.feature.freemium.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Country
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.domain.session.FakeCurrentCountryProvider
import com.danzucker.stitchpad.feature.freemium.domain.BillingCadence
import com.danzucker.stitchpad.feature.freemium.domain.CheckoutOutcome
import com.danzucker.stitchpad.feature.freemium.domain.PaymentError
import com.danzucker.stitchpad.feature.freemium.domain.PaymentRepository
import com.danzucker.stitchpad.feature.freemium.domain.StoreProductIds
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutingPaymentRepositoryTest {

    private class RecordingPaymentRepository(
        private val outcome: CheckoutOutcome,
        private val catalog: Map<String, String> = emptyMap(),
    ) : PaymentRepository {
        val calls = mutableListOf<String>()

        override suspend fun startCheckout(
            tier: SubscriptionTier,
            cadence: BillingCadence,
        ): Result<CheckoutOutcome, PaymentError> {
            calls += "checkout:$tier:$cadence"
            return Result.Success(outcome)
        }

        override suspend fun productCatalog(): Result<Map<String, String>, PaymentError> {
            calls += "catalog"
            return Result.Success(catalog)
        }

        override suspend fun restorePurchases(): Result<CheckoutOutcome, PaymentError> {
            calls += "restore"
            return Result.Success(outcome)
        }
    }

    private val paystack = RecordingPaymentRepository(CheckoutOutcome.Redirect("https://paystack/x", "ref"))
    private val play = RecordingPaymentRepository(
        CheckoutOutcome.PurchasedAndGranted,
        catalog = mapOf(StoreProductIds.PRO_MONTHLY to "GH₵ 15.00"),
    )

    private fun repo(country: FakeCurrentCountryProvider) =
        RoutingPaymentRepository(paystack = paystack, play = play, countryProvider = country)

    @Test
    fun routing_ng_usesPaystack() = runTest {
        val result = repo(FakeCurrentCountryProvider(Country.NG)).startCheckout(SubscriptionTier.PRO, BillingCadence.MONTHLY)
        assertTrue(result is Result.Success && result.data is CheckoutOutcome.Redirect)
        assertEquals(listOf("checkout:PRO:MONTHLY"), paystack.calls)
        assertTrue(play.calls.isEmpty())
    }

    @Test
    fun routing_gh_usesPlay() = runTest {
        val routing = repo(FakeCurrentCountryProvider(Country.GH))
        val result = routing.startCheckout(SubscriptionTier.ATELIER, BillingCadence.ANNUAL)
        routing.restorePurchases()
        assertEquals(Result.Success(CheckoutOutcome.PurchasedAndGranted), result)
        assertEquals(listOf("checkout:ATELIER:ANNUAL", "restore"), play.calls)
        assertTrue(paystack.calls.isEmpty())
    }

    @Test
    fun routing_nullLoadedCountry_returnsProviderUnavailable() = runTest {
        val routing = repo(FakeCurrentCountryProvider(Country.GH, loaded = false))
        assertEquals(
            Result.Error(PaymentError.PROVIDER_UNAVAILABLE),
            routing.startCheckout(SubscriptionTier.PRO, BillingCadence.MONTHLY),
        )
        assertEquals(Result.Error(PaymentError.PROVIDER_UNAVAILABLE), routing.restorePurchases())
        assertTrue(paystack.calls.isEmpty())
        assertTrue(play.calls.isEmpty())
    }

    @Test
    fun routing_catalog_ngIsEmpty() = runTest {
        assertEquals(Result.Success(emptyMap()), repo(FakeCurrentCountryProvider(Country.NG)).productCatalog())
        assertEquals(
            Result.Success(emptyMap()),
            repo(FakeCurrentCountryProvider(Country.GH, loaded = false)).productCatalog(),
        )
        assertTrue(play.calls.isEmpty())
        assertTrue(paystack.calls.isEmpty())
    }

    @Test
    fun routing_catalog_ghDelegatesToPlay() = runTest {
        assertEquals(
            Result.Success(mapOf(StoreProductIds.PRO_MONTHLY to "GH₵ 15.00")),
            repo(FakeCurrentCountryProvider(Country.GH)).productCatalog(),
        )
        assertEquals(listOf("catalog"), play.calls)
    }
}
