@file:Suppress("TooManyFunctions")

package com.danzucker.stitchpad.feature.onboarding.data

interface OnboardingPreferencesStore {
    suspend fun hasSeenOnboarding(): Boolean
    suspend fun setOnboardingSeen()
    suspend fun hasCompletedWorkshopSetup(): Boolean
    suspend fun setWorkshopSetupCompleted()

    /**
     * Per-user cache of "this account's remote profile already has workshop data", set by
     * the launch router after a one-shot Firestore check on a fresh install (the reinstall
     * case). Scoped to [userId] — unlike [hasCompletedWorkshopSetup], which is device-wide —
     * so confirming one account never lets a different account on the same device skip setup.
     */
    suspend fun hasConfirmedRemoteWorkshopProfile(userId: String): Boolean
    suspend fun setConfirmedRemoteWorkshopProfile(userId: String)

    /**
     * Whether email verification has been bypassed on this device. Set only by
     * the debug-build "skip verification" affordance; always false in release
     * builds (no code path sets it). Used by the auth gate to let QA proceed
     * without a real inbox round-trip.
     */
    suspend fun hasBypassedEmailVerification(): Boolean
    suspend fun setEmailVerificationBypassed()

    /**
     * Whether the Android push-permission pre-prompt has been shown on this device.
     * Once set we never show the sheet again — even if the user dismissed it — to
     * avoid nagging. On iOS this flag is never set (iOS handles its own permission
     * flow via UNUserNotificationCenter).
     */
    suspend fun hasAskedPushPermission(): Boolean
    suspend fun setAskedPushPermission()

    /**
     * Resets all onboarding flags to false. Debug-menu use only — production
     * code should not call this. Idempotent.
     */
    suspend fun resetForDebug()
}
