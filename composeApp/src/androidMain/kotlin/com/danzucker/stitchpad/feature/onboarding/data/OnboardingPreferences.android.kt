package com.danzucker.stitchpad.feature.onboarding.data

import android.content.Context
import android.content.SharedPreferences

actual class OnboardingPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "stitchpad_prefs",
        Context.MODE_PRIVATE
    )

    actual suspend fun hasSeenOnboarding(): Boolean {
        return prefs.getBoolean(KEY_HAS_SEEN_ONBOARDING, false)
    }

    actual suspend fun setOnboardingSeen() {
        prefs.edit().putBoolean(KEY_HAS_SEEN_ONBOARDING, true).apply()
    }

    companion object {
        private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
    }
}
