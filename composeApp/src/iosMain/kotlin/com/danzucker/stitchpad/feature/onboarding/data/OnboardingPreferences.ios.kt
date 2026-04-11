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

    companion object {
        private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
        private const val KEY_HAS_COMPLETED_WORKSHOP = "has_completed_workshop_setup"
    }
}
