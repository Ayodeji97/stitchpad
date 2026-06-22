package com.danzucker.stitchpad.feature.onboarding.data

class FakeOnboardingPreferences : OnboardingPreferencesStore {
    var onboardingSeen = false
    val completedWorkshopSetups = mutableSetOf<String>()
    val confirmedRemoteWorkshopProfiles = mutableSetOf<String>()
    var emailVerificationBypassed = false
    var askedPushPermission = false

    override suspend fun hasSeenOnboarding() = onboardingSeen
    override suspend fun setOnboardingSeen() { onboardingSeen = true }
    override suspend fun hasCompletedWorkshopSetup(userId: String) =
        completedWorkshopSetups.contains(userId)
    override suspend fun setWorkshopSetupCompleted(userId: String) {
        completedWorkshopSetups.add(userId)
    }
    override suspend fun hasConfirmedRemoteWorkshopProfile(userId: String) =
        confirmedRemoteWorkshopProfiles.contains(userId)
    override suspend fun setConfirmedRemoteWorkshopProfile(userId: String) {
        confirmedRemoteWorkshopProfiles.add(userId)
    }
    override suspend fun hasBypassedEmailVerification() = emailVerificationBypassed
    override suspend fun setEmailVerificationBypassed() { emailVerificationBypassed = true }
    override suspend fun hasAskedPushPermission() = askedPushPermission
    override suspend fun setAskedPushPermission() { askedPushPermission = true }
    override suspend fun resetForDebug() {
        onboardingSeen = false
        completedWorkshopSetups.clear()
        confirmedRemoteWorkshopProfiles.clear()
        emailVerificationBypassed = false
        askedPushPermission = false
    }
}
