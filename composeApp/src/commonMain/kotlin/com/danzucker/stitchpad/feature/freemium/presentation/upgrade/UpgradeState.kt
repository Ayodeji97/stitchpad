package com.danzucker.stitchpad.feature.freemium.presentation.upgrade

import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.feature.freemium.domain.BillingCadence
import com.danzucker.stitchpad.feature.freemium.domain.CheckoutProvider
import com.danzucker.stitchpad.feature.freemium.presentation.PlanPriceText

data class UpgradeState(
    val currentTier: SubscriptionTier = SubscriptionTier.FREE,
    val selectedTier: SubscriptionTier = SubscriptionTier.PRO,
    val billingCadence: BillingCadence = BillingCadence.MONTHLY,
    val isStartingCheckout: Boolean = false,
    // Which checkout path this purchase takes, so the CTA reads "Subscribe" (Apple
    // or Google Play, native sheet) vs "Pay with Paystack" (web, Nigeria). Apple
    // Guideline 3.1.1 forbids any Paystack/web reference on iOS. Null on Android
    // until the workshop country loads: no checkout, never a Paystack fallback.
    val checkoutProvider: CheckoutProvider? = CheckoutProvider.PAYSTACK,
    // Localized store display prices (App Store or Google Play) keyed by
    // StoreProductIds. The tier cards must show what the store actually charges,
    // not the bundled NGN strings. Empty on the Paystack path / before products load.
    val storeDisplayPrices: Map<String, String> = emptyMap(),
    // Server-controlled (config/app) flag: is Android Paystack billing live? Drives
    // the Paystack "Coming soon" gate below. Irrelevant on the store paths.
    val billingEnabled: Boolean = false,
    // Server-controlled (config/app) flag: is Google Play Billing live? Gates the
    // Play path exactly like billingEnabled gates Paystack.
    val playBillingEnabled: Boolean = false,
    // Paystack (Android) tier prices, formatted for the active workshop's currency.
    // Always the real NGN amounts — never converted — labelled "NGN" outside Nigeria.
    // Unused on iOS, where the StoreKit prices above apply.
    val paystackPrices: Map<SubscriptionTier, PlanPriceText> = emptyMap(),
    // True when the workshop's currency isn't NGN, so the Paystack screen states that
    // plans are billed in Naira.
    val showBillingCurrencyNote: Boolean = false,
) {
    /**
     * Whether the Pay CTA may initiate checkout. Apple (iOS) is always allowed:
     * its availability is gated by App Store approval. Paystack requires the server
     * [billingEnabled] flag and Google Play requires [playBillingEnabled], so the
     * CTA shows "Coming soon" until that path is flipped live. No provider yet
     * (country still loading) means no checkout.
     */
    val canCheckout: Boolean
        get() = when (checkoutProvider) {
            null -> false
            CheckoutProvider.APPLE -> true
            CheckoutProvider.PLAY -> playBillingEnabled
            CheckoutProvider.PAYSTACK -> billingEnabled
        }

    /** App Store and Google Play both sell auto-renewing subscriptions through a native sheet. */
    val isStoreCheckout: Boolean
        get() = checkoutProvider == CheckoutProvider.APPLE || checkoutProvider == CheckoutProvider.PLAY
}
