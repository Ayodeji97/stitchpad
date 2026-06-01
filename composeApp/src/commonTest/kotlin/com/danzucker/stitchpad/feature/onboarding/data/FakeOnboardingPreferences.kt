package com.danzucker.stitchpad.feature.onboarding.data

class FakeOnboardingPreferences : OnboardingPreferencesStore {
    var onboardingSeen = false
    var workshopSetupCompleted = false
    var emailVerificationBypassed = false

    override suspend fun hasSeenOnboarding() = onboardingSeen
    override suspend fun setOnboardingSeen() { onboardingSeen = true }
    override suspend fun hasCompletedWorkshopSetup() = workshopSetupCompleted
    override suspend fun setWorkshopSetupCompleted() { workshopSetupCompleted = true }
    override suspend fun hasBypassedEmailVerification() = emailVerificationBypassed
    override suspend fun setEmailVerificationBypassed() { emailVerificationBypassed = true }
    override suspend fun resetForDebug() {
        onboardingSeen = false
        workshopSetupCompleted = false
        emailVerificationBypassed = false
    }
}
