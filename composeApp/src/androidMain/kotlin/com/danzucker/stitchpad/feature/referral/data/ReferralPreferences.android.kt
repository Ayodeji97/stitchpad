package com.danzucker.stitchpad.feature.referral.data

import android.content.Context
import android.content.SharedPreferences
import com.danzucker.stitchpad.feature.referral.domain.ReferralPreferencesStore
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

actual class ReferralPreferences(context: Context) : ReferralPreferencesStore {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "referral_prefs",
        Context.MODE_PRIVATE
    )

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun getOrCreateDeviceId(): String {
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
        val id = Uuid.random().toString()
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    override suspend fun hasAttributed(): Boolean = prefs.getBoolean(KEY_ATTRIBUTED, false)

    override suspend fun setAttributed() {
        prefs.edit().putBoolean(KEY_ATTRIBUTED, true).apply()
    }

    override suspend fun hasCheckedReferrer(): Boolean = prefs.getBoolean(KEY_REFERRER_CHECKED, false)

    override suspend fun setReferrerChecked() {
        prefs.edit().putBoolean(KEY_REFERRER_CHECKED, true).apply()
    }

    override suspend fun hasCheckedClipboard(): Boolean = prefs.getBoolean(KEY_CLIPBOARD_CHECKED, false)

    override suspend fun setClipboardChecked() {
        prefs.edit().putBoolean(KEY_CLIPBOARD_CHECKED, true).apply()
    }

    override suspend fun resetForDebug() {
        prefs.edit()
            .putBoolean(KEY_ATTRIBUTED, false)
            .putBoolean(KEY_REFERRER_CHECKED, false)
            .putBoolean(KEY_CLIPBOARD_CHECKED, false)
            .apply()
    }

    private companion object {
        const val KEY_DEVICE_ID = "referral_device_id"
        const val KEY_ATTRIBUTED = "referral_attributed"
        const val KEY_REFERRER_CHECKED = "referral_referrer_checked"
        const val KEY_CLIPBOARD_CHECKED = "referral_clipboard_checked"
    }
}
