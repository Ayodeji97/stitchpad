package com.danzucker.stitchpad.feature.referral.data

import com.danzucker.stitchpad.feature.referral.domain.ReferralPreferencesStore

/** Test double for [ReferralPreferencesStore]. */
class FakeReferralPreferencesStore : ReferralPreferencesStore {
    var deviceId: String = "device-abc-123"
    var attributed: Boolean = false
    var referrerChecked: Boolean = false
    var clipboardChecked: Boolean = false
    var resetForDebugCallCount: Int = 0

    override suspend fun getOrCreateDeviceId(): String = deviceId
    override suspend fun hasAttributed(): Boolean = attributed
    override suspend fun setAttributed() { attributed = true }
    override suspend fun hasCheckedReferrer(): Boolean = referrerChecked
    override suspend fun setReferrerChecked() { referrerChecked = true }
    override suspend fun hasCheckedClipboard(): Boolean = clipboardChecked
    override suspend fun setClipboardChecked() { clipboardChecked = true }

    override suspend fun resetForDebug() {
        resetForDebugCallCount++
        attributed = false
        referrerChecked = false
        clipboardChecked = false
    }
}
