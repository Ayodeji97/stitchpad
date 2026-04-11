package com.danzucker.stitchpad.feature.onboarding.data

class FakeOnboardingPreferences : OnboardingPreferencesStore {
    var onboardingSeen = false
    var workshopSetupCompleted = false

    override suspend fun hasSeenOnboarding() = onboardingSeen
    override suspend fun setOnboardingSeen() { onboardingSeen = true }
    override suspend fun hasCompletedWorkshopSetup() = workshopSetupCompleted
    override suspend fun setWorkshopSetupCompleted() { workshopSetupCompleted = true }
}
