package com.danzucker.stitchpad.feature.billing.domain

import kotlinx.coroutines.flow.Flow

/**
 * Read + debug-toggle access to the user's premium entitlement.
 *
 * V2 ships with [InMemoryEntitlementsRepository] returning `true` by default —
 * everyone sees the premium Reports surface during preview. When real billing
 * lands (Google Play Billing / RevenueCat), swap the binding in [billingModule]
 * for a network-backed implementation; the rest of the app keeps consuming
 * [observeIsPremium] unchanged.
 */
interface EntitlementsRepository {
    fun observeIsPremium(): Flow<Boolean>

    /**
     * Debug-only toggle so a Settings switch can preview the paywall card
     * without wiring real billing. Real implementations should make this a
     * no-op (or only honor it in debug builds).
     */
    suspend fun setIsPremium(value: Boolean)
}
