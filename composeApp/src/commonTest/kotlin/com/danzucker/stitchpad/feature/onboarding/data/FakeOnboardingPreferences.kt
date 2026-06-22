package com.danzucker.stitchpad.feature.onboarding.data

class FakeOnboardingPreferences : OnboardingPreferencesStore {
    var onboardingSeen = false
    var workshopSetupCompleted = false
    val confirmedRemoteWorkshopProfiles = mutableSetOf<String>()
    var emailVerificationBypassed = false
    var askedPushPermission = false

    override suspend fun hasSeenOnboarding() = onboardingSeen
    override suspend fun setOnboardingSeen() { onboardingSeen = true }
    override suspend fun hasCompletedWorkshopSetup() = workshopSetupCompleted
    override suspend fun setWorkshopSetupCompleted() { workshopSetupCompleted = true }
    override suspend fun hasConfirmedRemoteWorkshopProfile(userId: String) =
        confirmedRemoteWorkshopProfiles.contains(userId)
    override suspend fun setConfirmedRemoteWorkshopProfile(userId: String) {
        confirmedRemoteWorkshopProfiles.add(userId)
    }
    override suspend fun hasBypassedEmailVerification() = emailVerificationBypassed
    override suspend fun setEmailVerificationBypassed() { emailVerificationBypassed = true }
    override suspend fun hasAskedPushPermission() = askedPushPermission
    override suspend fun setAskedPushPermission() { askedPushPermission = true }
    var communityBannerDismissed = false

    override suspend fun hasDismissedCommunityBanner(): Boolean = communityBannerDismissed
    override suspend fun setCommunityBannerDismissed() { communityBannerDismissed = true }
    override suspend fun clearCommunityBannerDismissed() { communityBannerDismissed = false }
    override suspend fun resetForDebug() {
        onboardingSeen = false
        workshopSetupCompleted = false
        confirmedRemoteWorkshopProfiles.clear()
        emailVerificationBypassed = false
        askedPushPermission = false
    }
}
