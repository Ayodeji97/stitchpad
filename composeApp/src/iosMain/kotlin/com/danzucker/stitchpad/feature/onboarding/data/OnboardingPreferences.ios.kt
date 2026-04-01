package com.danzucker.stitchpad.feature.onboarding.data

import platform.Foundation.NSUserDefaults

actual class OnboardingPreferences {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual suspend fun hasSeenOnboarding(): Boolean {
        return defaults.boolForKey(KEY_HAS_SEEN_ONBOARDING)
    }

    actual suspend fun setOnboardingSeen() {
        defaults.setBool(true, forKey = KEY_HAS_SEEN_ONBOARDING)
    }

    companion object {
        private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
    }
}
