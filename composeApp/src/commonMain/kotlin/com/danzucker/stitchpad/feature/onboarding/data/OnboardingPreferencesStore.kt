package com.danzucker.stitchpad.feature.onboarding.data

interface OnboardingPreferencesStore {
    suspend fun hasSeenOnboarding(): Boolean
    suspend fun setOnboardingSeen()
    suspend fun hasCompletedWorkshopSetup(): Boolean
    suspend fun setWorkshopSetupCompleted()

    /**
     * Resets all onboarding flags to false. Debug-menu use only — production
     * code should not call this. Idempotent.
     */
    suspend fun resetForDebug()
}
