package com.danzucker.stitchpad.feature.freemium.presentation.upgrade

import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.feature.freemium.domain.BillingCadence

sealed interface UpgradeAction {
    data class SelectTier(val tier: SubscriptionTier) : UpgradeAction
    data class SelectCadence(val cadence: BillingCadence) : UpgradeAction
    data object PayWithPaystack : UpgradeAction
    data object ConfirmCheckout : UpgradeAction
    data object DismissCheckoutSheet : UpgradeAction
}
