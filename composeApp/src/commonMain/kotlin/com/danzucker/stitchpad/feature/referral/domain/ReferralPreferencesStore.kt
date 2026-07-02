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

    /**
     * True once we've read the Play Install Referrer and found it carries no code
     * (organic install). Bounds the Play-service bind to once per install so organic
     * users don't re-read on every launch. A genuine-but-unsubmitted referrer leaves
     * this false so the read (and attribution retry) runs again next launch.
     */
    suspend fun hasCheckedReferrer(): Boolean

    suspend fun setReferrerChecked()

    /** Debug menu: clear the attributed + referrer-checked flags (device id stays stable). */
    suspend fun resetForDebug()
}
