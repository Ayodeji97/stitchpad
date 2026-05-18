package com.danzucker.stitchpad.core.domain.entitlement

import kotlinx.coroutines.flow.StateFlow

/**
 * Read-side handle for the current signed-in user's entitlements.
 * Implementations hot-cache the latest user-doc snapshot and
 * re-compute on changes to subscriptionTier / welcomeBonusAppliedAt.
 * Resets to FREE defaults on sign-out.
 */
interface EntitlementsProvider {
    /** Fast synchronous read — last computed snapshot. */
    fun current(): UserEntitlements

    /** Hot flow for reactive observers (banner, customer-list ViewModel). */
    val flow: StateFlow<UserEntitlements>
}
