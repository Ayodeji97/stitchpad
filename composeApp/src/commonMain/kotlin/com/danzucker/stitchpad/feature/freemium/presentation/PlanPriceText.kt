package com.danzucker.stitchpad.feature.freemium.presentation

import com.danzucker.stitchpad.core.domain.model.Currency
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.sharing.formatMoney
import com.danzucker.stitchpad.feature.freemium.domain.PaystackPlanPrices

/** Pre-formatted Paystack prices for one tier, ready for the `%1$s` placeholders. */
data class PlanPriceText(
    val monthly: String,
    val annual: String,
    /** What the annual plan saves against twelve monthly payments. */
    val annualSaving: String,
)

/**
 * Formats a Paystack plan amount for a workshop that keeps its books in [workshopCurrency].
 *
 * Plans are billed only in [PaystackPlanPrices.currency] (NGN) and never converted. A
 * Nigerian workshop sees the familiar symbol ("₦2,000"). Any other workshop sees the ISO
 * code ("NGN 2,000") so the amount reads unambiguously as Naira — the currency the charge
 * is actually made in — rather than a bare ₦ that looks like a local price or a typo.
 */
fun formatPlanPrice(amount: Double, workshopCurrency: Currency): String {
    val billing = PaystackPlanPrices.currency
    return if (workshopCurrency == billing) {
        formatMoney(amount, billing)
    } else {
        "${billing.code} ${formatMoney(amount, billing, includeSymbol = false)}"
    }
}

/** The tier's Paystack prices, or null for [SubscriptionTier.FREE] (nothing to buy). */
fun paystackPlanPriceText(tier: SubscriptionTier, workshopCurrency: Currency): PlanPriceText? {
    val monthly = PaystackPlanPrices.monthly(tier)
    val annual = PaystackPlanPrices.annual(tier)
    if (monthly == null || annual == null) return null
    return PlanPriceText(
        monthly = formatPlanPrice(monthly, workshopCurrency),
        annual = formatPlanPrice(annual, workshopCurrency),
        annualSaving = formatPlanPrice(monthly * MONTHS_PER_YEAR - annual, workshopCurrency),
    )
}

/**
 * The Pro monthly price for a compact upgrade CTA ("Upgrade — ₦2,000/mo"), or null when
 * the CTA must stay neutral. Apple (iOS) charges the StoreKit price of the tailor's App
 * Store storefront, which these screens don't load, so quoting the Paystack amount there
 * would show a price Apple doesn't charge.
 */
fun upgradeCtaPrice(isAppleCheckout: Boolean, workshopCurrency: Currency): String? =
    if (isAppleCheckout) null else paystackPlanPriceText(SubscriptionTier.PRO, workshopCurrency)?.monthly

/** True when the workshop's money is not the Paystack billing currency, so the charge is foreign to it. */
fun isBilledInForeignCurrency(workshopCurrency: Currency): Boolean =
    workshopCurrency != PaystackPlanPrices.currency

private const val MONTHS_PER_YEAR = 12
