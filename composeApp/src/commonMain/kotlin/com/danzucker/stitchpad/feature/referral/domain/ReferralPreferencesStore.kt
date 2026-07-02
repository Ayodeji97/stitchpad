package com.danzucker.stitchpad.feature.referral.domain

/**
 * Persisted, per-install referral state. Backed by SharedPreferences on Android and
 * NSUserDefaults on iOS (see the platform actuals of ReferralPreferences).
 */
interface ReferralPreferencesStore {
    /**
     * A stable, opaque per-install id used as the server's device-reuse dedupe key.
     * Generated once (a random UUID) and persisted; resets on reinstall, so device
     * dedupe is best-effort by design.
     */
    suspend fun getOrCreateDeviceId(): String

    /** True once an attribution call has succeeded — the guard against re-submitting. */
    suspend fun hasAttributed(): Boolean

    suspend fun setAttributed()

    /** Debug menu: clear the attributed flag (keeps the device id stable). */
    suspend fun resetForDebug()
}
