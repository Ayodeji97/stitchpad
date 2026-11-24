package com.danzucker.stitchpad.feature.freemium.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.AndroidBilling
import com.danzucker.stitchpad.core.domain.model.Country
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.domain.session.CurrentCountryProvider
import com.danzucker.stitchpad.feature.freemium.domain.BillingCadence
import com.danzucker.stitchpad.feature.freemium.domain.CheckoutOutcome
import com.danzucker.stitchpad.feature.freemium.domain.PaymentError
import com.danzucker.stitchpad.feature.freemium.domain.PaymentRepository

/**
 * Android payment entry point: picks Paystack (Nigeria) or Google Play (every
 * other wave-1 market) per call from the workshop's LOADED country. Until the
 * country is known there is no checkout at all. Falling back to
 * [Country.DEFAULT] (NG) would send a Ghana tailor to a Paystack redirect the
 * server refuses.
 */
internal class RoutingPaymentRepository(
    private val paystack: PaymentRepository,
    private val play: PaymentRepository,
    private val countryProvider: CurrentCountryProvider,
) : PaymentRepository {

    override suspend fun startCheckout(
        tier: SubscriptionTier,
        cadence: BillingCadence,
    ): Result<CheckoutOutcome, PaymentError> {
        val country = countryProvider.countryForWrite() ?: return Result.Error(PaymentError.PROVIDER_UNAVAILABLE)
        return delegateFor(country).startCheckout(tier, cadence)
    }

    override suspend fun productCatalog(): Result<Map<String, String>, PaymentError> {
        val country = countryProvider.loadedCountry.value
        // Paystack shows bundled NGN prices; an unknown country shows nothing yet.
        if (country == null || country.androidBilling != AndroidBilling.PLAY) return Result.Success(emptyMap())
        return play.productCatalog()
    }

    override suspend fun restorePurchases(): Result<CheckoutOutcome, PaymentError> {
        val country = countryProvider.countryForWrite() ?: return Result.Error(PaymentError.PROVIDER_UNAVAILABLE)
        return delegateFor(country).restorePurchases()
    }

    private fun delegateFor(country: Country): PaymentRepository =
        if (country.androidBilling == AndroidBilling.PLAY) play else paystack
}
