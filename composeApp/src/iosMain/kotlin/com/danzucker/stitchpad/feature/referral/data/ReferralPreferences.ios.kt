package com.danzucker.stitchpad.feature.referral.data

import com.danzucker.stitchpad.feature.referral.domain.ReferralPreferencesStore
import platform.Foundation.NSUserDefaults
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

actual class ReferralPreferences : ReferralPreferencesStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun getOrCreateDeviceId(): String {
        defaults.stringForKey(KEY_DEVICE_ID)?.let { return it }
        val id = Uuid.random().toString()
        defaults.setObject(id, forKey = KEY_DEVICE_ID)
        return id
    }

    override suspend fun hasAttributed(): Boolean = defaults.boolForKey(KEY_ATTRIBUTED)

    override suspend fun setAttributed() {
        defaults.setBool(true, forKey = KEY_ATTRIBUTED)
    }

    override suspend fun hasCheckedReferrer(): Boolean = defaults.boolForKey(KEY_REFERRER_CHECKED)

    override suspend fun setReferrerChecked() {
        defaults.setBool(true, forKey = KEY_REFERRER_CHECKED)
    }

    override suspend fun hasCheckedClipboard(): Boolean = defaults.boolForKey(KEY_CLIPBOARD_CHECKED)

    override suspend fun setClipboardChecked() {
        defaults.setBool(true, forKey = KEY_CLIPBOARD_CHECKED)
    }

    override suspend fun resetForDebug() {
        defaults.setBool(false, forKey = KEY_ATTRIBUTED)
        defaults.setBool(false, forKey = KEY_REFERRER_CHECKED)
        defaults.setBool(false, forKey = KEY_CLIPBOARD_CHECKED)
    }

    private companion object {
        const val KEY_DEVICE_ID = "referral_device_id"
        const val KEY_ATTRIBUTED = "referral_attributed"
        const val KEY_REFERRER_CHECKED = "referral_referrer_checked"
        const val KEY_CLIPBOARD_CHECKED = "referral_clipboard_checked"
    }
}
