package com.danzucker.stitchpad.feature.freemium.domain

import com.danzucker.stitchpad.core.domain.model.SubscriptionTier

/**
 * App Store Connect product identifiers for the auto-renewable subscriptions.
 * MUST stay in sync with the server-side PRODUCT_MAP in
 * functions/src/billing/appleBilling.ts and the products configured in App Store
 * Connect. The server is the authoritative tier↔product mapping; this client copy
 * only picks which product to purchase and which price to display on iOS.
 */
object AppleProductIds {
    const val PRO_MONTHLY = "com.danzucker.stitchpad.pro.monthly"
    const val PRO_ANNUAL = "com.danzucker.stitchpad.pro.annual"
    const val ATELIER_MONTHLY = "com.danzucker.stitchpad.atelier.monthly"
    const val ATELIER_ANNUAL = "com.danzucker.stitchpad.atelier.annual"

    val ALL: List<String> = listOf(PRO_MONTHLY, PRO_ANNUAL, ATELIER_MONTHLY, ATELIER_ANNUAL)

    fun idFor(tier: SubscriptionTier, cadence: BillingCadence): String? = when (tier) {
        SubscriptionTier.PRO -> when (cadence) {
            BillingCadence.MONTHLY -> PRO_MONTHLY
            BillingCadence.ANNUAL -> PRO_ANNUAL
        }
        SubscriptionTier.ATELIER -> when (cadence) {
            BillingCadence.MONTHLY -> ATELIER_MONTHLY
            BillingCadence.ANNUAL -> ATELIER_ANNUAL
        }
        SubscriptionTier.FREE -> null
    }
}
