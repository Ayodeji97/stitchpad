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

    /**
     * True once we've read the iOS clipboard for a referral link and found none.
     * Bounds the clipboard read (and its "pasted from" banner) to once per install;
     * left false while a genuine clipboard code fails to submit so it retries.
     */
    suspend fun hasCheckedClipboard(): Boolean

    suspend fun setClipboardChecked()

    /** Debug menu: clear the attributed + checked flags (device id stays stable). */
    suspend fun resetForDebug()
}
