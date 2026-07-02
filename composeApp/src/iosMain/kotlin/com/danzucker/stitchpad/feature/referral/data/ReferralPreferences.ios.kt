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

    override suspend fun resetForDebug() {
        defaults.setBool(false, forKey = KEY_ATTRIBUTED)
    }

    private companion object {
        const val KEY_DEVICE_ID = "referral_device_id"
        const val KEY_ATTRIBUTED = "referral_attributed"
    }
}
