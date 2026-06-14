package com.danzucker.stitchpad.feature.freemium.presentation.upgrade

import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.feature.freemium.domain.BillingCadence

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
)

enum class CheckoutProvider { PAYSTACK, APPLE }
