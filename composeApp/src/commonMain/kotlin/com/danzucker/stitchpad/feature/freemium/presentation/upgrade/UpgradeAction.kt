package com.danzucker.stitchpad.feature.freemium.presentation.upgrade

import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.feature.freemium.domain.BillingCadence

sealed interface UpgradeAction {
    data class SelectTier(val tier: SubscriptionTier) : UpgradeAction
    data class SelectCadence(val cadence: BillingCadence) : UpgradeAction

    // Provider-neutral: routes to Paystack (Android) or Apple IAP (iOS) depending
    // on the platform-bound PaymentRepository.
    data object StartCheckout : UpgradeAction

    // Apple's required "Restore purchases" affordance (shown only on iOS).
    data object RestorePurchases : UpgradeAction

    // Open the legal pages in the browser. Apple Guideline 3.1.2 wants functional
    // Privacy Policy + Terms of Use (EULA) links on/near the subscription paywall.
    data object OnPrivacyClick : UpgradeAction
    data object OnTermsClick : UpgradeAction
}
