package com.danzucker.stitchpad.core.domain.entitlement

import kotlinx.coroutines.flow.StateFlow

/**
 * Read-side handle for the current signed-in user's entitlements.
 * Implementations hot-cache the latest user-doc snapshot and
 * re-compute on changes to subscriptionTier / welcomeBonusAppliedAt.
 * Resets to FREE defaults on sign-out.
 */
interface EntitlementsProvider {
    /**
     * Fast synchronous read — last computed snapshot. WARNING: may return the
     * default FREE/15 snapshot before the first Firestore emission lands; use
     * [awaitHydrated] in gates where treating an unhydrated user as FREE would
     * produce a wrong answer (e.g. cap enforcement on Pro/Atelier accounts).
     */
    fun current(): UserEntitlements

    /** Hot flow for reactive observers (banner, customer-list ViewModel). */
    val flow: StateFlow<UserEntitlements>

    /**
     * Suspends until the first Firestore-sourced snapshot for the currently
     * signed-in user has been applied, then returns the live snapshot. Use
     * this in any synchronous gate (e.g. createCustomer cap check) where the
     * default FREE/15 placeholder could incorrectly block a Pro/Atelier or
     * First Month user on cold start. Callers must already be inside an
     * auth-gated path — if the user signs out while awaiting, the call will
     * suspend until the next sign-in's snapshot arrives.
     */
    suspend fun awaitHydrated(): UserEntitlements
}
