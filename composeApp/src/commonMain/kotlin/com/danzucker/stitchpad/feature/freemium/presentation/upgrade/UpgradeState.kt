package com.danzucker.stitchpad.feature.freemium.presentation.upgrade

import com.danzucker.stitchpad.core.domain.model.SubscriptionTier

data class UpgradeState(
    val currentTier: SubscriptionTier = SubscriptionTier.FREE,
    val selectedTier: SubscriptionTier = SubscriptionTier.PRO,
    val billingCadence: BillingCadence = BillingCadence.MONTHLY,
)

enum class BillingCadence { MONTHLY, ANNUAL }
