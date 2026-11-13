package com.danzucker.stitchpad.feature.freemium.domain

import com.danzucker.stitchpad.core.domain.model.Currency
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier

/**
 * The Paystack plan catalogue, in major units of its single billing currency.
 *
 * Mirrors `PRICES_KOBO` / `CURRENCY` in functions/src/billing/paystackBilling.ts,
 * which is the source of truth: the server initialises every checkout at these
 * amounts in NGN and rejects a verification for any other amount or currency.
 * There are no per-country prices — a Ghanaian or Senegalese workshop paying
 * through Paystack is charged these Naira amounts — so the app never converts
 * them into the workshop's currency.
 */
object PaystackPlanPrices {
    val currency: Currency = Currency.NGN

    fun monthly(tier: SubscriptionTier): Double? = when (tier) {
        SubscriptionTier.FREE -> null
        SubscriptionTier.PRO -> PRO_MONTHLY
        SubscriptionTier.ATELIER -> ATELIER_MONTHLY
    }

    fun annual(tier: SubscriptionTier): Double? = when (tier) {
        SubscriptionTier.FREE -> null
        SubscriptionTier.PRO -> PRO_ANNUAL
        SubscriptionTier.ATELIER -> ATELIER_ANNUAL
    }

    private const val PRO_MONTHLY = 2_000.0
    private const val PRO_ANNUAL = 20_000.0
    private const val ATELIER_MONTHLY = 4_000.0
    private const val ATELIER_ANNUAL = 40_000.0
}
