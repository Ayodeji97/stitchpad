package com.danzucker.stitchpad.feature.freemium.presentation.upgrade

import com.danzucker.stitchpad.core.domain.model.SubscriptionTier

sealed interface UpgradeAction {
    data class SelectTier(val tier: SubscriptionTier) : UpgradeAction
    data class SelectCadence(val cadence: BillingCadence) : UpgradeAction
    data object PayWithPaystack : UpgradeAction
}
