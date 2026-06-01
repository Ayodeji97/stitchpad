package com.danzucker.stitchpad.feature.onboarding.data

import platform.Foundation.NSUserDefaults

actual class OnboardingPreferences : OnboardingPreferencesStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun hasSeenOnboarding(): Boolean {
        return defaults.boolForKey(KEY_HAS_SEEN_ONBOARDING)
    }

    override suspend fun setOnboardingSeen() {
        defaults.setBool(true, forKey = KEY_HAS_SEEN_ONBOARDING)
    }

    override suspend fun hasCompletedWorkshopSetup(): Boolean {
        return defaults.boolForKey(KEY_HAS_COMPLETED_WORKSHOP)
    }

    override suspend fun setWorkshopSetupCompleted() {
        defaults.setBool(true, forKey = KEY_HAS_COMPLETED_WORKSHOP)
    }

    override suspend fun hasBypassedEmailVerification(): Boolean {
        return defaults.boolForKey(KEY_BYPASSED_EMAIL_VERIFICATION)
    }

    override suspend fun setEmailVerificationBypassed() {
        defaults.setBool(true, forKey = KEY_BYPASSED_EMAIL_VERIFICATION)
    }

    override suspend fun resetForDebug() {
        defaults.setBool(false, forKey = KEY_HAS_SEEN_ONBOARDING)
        defaults.setBool(false, forKey = KEY_HAS_COMPLETED_WORKSHOP)
        defaults.setBool(false, forKey = KEY_BYPASSED_EMAIL_VERIFICATION)
    }

    companion object {
        private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
        private const val KEY_HAS_COMPLETED_WORKSHOP = "has_completed_workshop_setup"
        private const val KEY_BYPASSED_EMAIL_VERIFICATION = "bypassed_email_verification"
    }
}
