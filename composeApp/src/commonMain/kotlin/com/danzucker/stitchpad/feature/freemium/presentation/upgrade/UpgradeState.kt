package com.danzucker.stitchpad.feature.freemium.presentation.upgrade

import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.feature.freemium.domain.BillingCadence
import com.danzucker.stitchpad.feature.freemium.presentation.PlanPriceText

data class UpgradeState(
    val currentTier: SubscriptionTier = SubscriptionTier.FREE,
    val selectedTier: SubscriptionTier = SubscriptionTier.PRO,
    val billingCadence: BillingCadence = BillingCadence.MONTHLY,
    val isStartingCheckout: Boolean = false,
    // Which checkout path this build uses, so the CTA reads "Subscribe" (Apple,
    // native sheet) on iOS vs "Pay with Paystack" (web) on Android. Apple
    // Guideline 3.1.1 forbids any Paystack/web reference on iOS.
    val checkoutProvider: CheckoutProvider = CheckoutProvider.PAYSTACK,
    // Localized StoreKit display prices keyed by AppleProductIds (iOS only). The
    // tier cards must show the price Apple actually charges, not the bundled NGN
    // strings. Empty on Android / before products load.
    val appleDisplayPrices: Map<String, String> = emptyMap(),
    // Server-controlled (config/app) flag: is Android Paystack billing live? Drives
    // the Paystack-only "Coming soon" gate below. Irrelevant on iOS (Apple path).
    val billingEnabled: Boolean = false,
    // Paystack (Android) tier prices, formatted for the active workshop's currency.
    // Always the real NGN amounts — never converted — labelled "NGN" outside Nigeria.
    // Unused on iOS, where the StoreKit prices above apply.
    val paystackPrices: Map<SubscriptionTier, PlanPriceText> = emptyMap(),
    // True when the workshop's currency isn't NGN, so the Paystack screen states that
    // plans are billed in Naira.
    val showBillingCurrencyNote: Boolean = false,
) {
    /**
     * Whether the Pay CTA may initiate checkout. Apple (iOS) is always allowed —
     * its availability is gated by App Store approval. Paystack (Android) requires
     * the server [billingEnabled] flag, so the CTA shows "Coming soon" and can't
     * start a real checkout until Paystack billing is flipped live.
     */
    val canCheckout: Boolean
        get() = checkoutProvider == CheckoutProvider.APPLE || billingEnabled
}

enum class CheckoutProvider { PAYSTACK, APPLE }
