package com.danzucker.stitchpad.feature.onboarding.data

expect class OnboardingPreferences {
    suspend fun hasSeenOnboarding(): Boolean
    suspend fun setOnboardingSeen()
}
