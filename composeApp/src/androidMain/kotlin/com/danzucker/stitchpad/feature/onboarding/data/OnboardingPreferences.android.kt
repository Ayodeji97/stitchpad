package com.danzucker.stitchpad.feature.onboarding.data

import android.content.Context
import android.content.SharedPreferences

actual class OnboardingPreferences(context: Context) : OnboardingPreferencesStore {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "stitchpad_prefs",
        Context.MODE_PRIVATE
    )

    override suspend fun hasSeenOnboarding(): Boolean {
        return prefs.getBoolean(KEY_HAS_SEEN_ONBOARDING, false)
    }

    override suspend fun setOnboardingSeen() {
        prefs.edit().putBoolean(KEY_HAS_SEEN_ONBOARDING, true).apply()
    }

    override suspend fun hasCompletedWorkshopSetup(): Boolean {
        return prefs.getBoolean(KEY_HAS_COMPLETED_WORKSHOP, false)
    }

    override suspend fun setWorkshopSetupCompleted() {
        prefs.edit().putBoolean(KEY_HAS_COMPLETED_WORKSHOP, true).apply()
    }

    override suspend fun hasBypassedEmailVerification(): Boolean {
        return prefs.getBoolean(KEY_BYPASSED_EMAIL_VERIFICATION, false)
    }

    override suspend fun setEmailVerificationBypassed() {
        prefs.edit().putBoolean(KEY_BYPASSED_EMAIL_VERIFICATION, true).apply()
    }

    override suspend fun resetForDebug() {
        prefs.edit()
            .putBoolean(KEY_HAS_SEEN_ONBOARDING, false)
            .putBoolean(KEY_HAS_COMPLETED_WORKSHOP, false)
            .putBoolean(KEY_BYPASSED_EMAIL_VERIFICATION, false)
            .commit()
    }

    companion object {
        private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
        private const val KEY_HAS_COMPLETED_WORKSHOP = "has_completed_workshop_setup"
        private const val KEY_BYPASSED_EMAIL_VERIFICATION = "bypassed_email_verification"
    }
}
