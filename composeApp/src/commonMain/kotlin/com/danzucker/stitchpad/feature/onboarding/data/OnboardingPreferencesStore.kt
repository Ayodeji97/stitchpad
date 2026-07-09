@file:Suppress("TooManyFunctions")

package com.danzucker.stitchpad.feature.onboarding.data

interface OnboardingPreferencesStore {
    suspend fun hasSeenOnboarding(): Boolean
    suspend fun setOnboardingSeen()

    /**
     * Per-user "this account finished (or skipped) workshop setup on this device". Scoped
     * to [userId] — NOT device-wide — so completing/skipping setup on one account never
     * lets a different account on the same install skip its own setup. Wiped on uninstall;
     * the remote-profile fallback in ResolveNeedsWorkshopSetup covers the reinstall case.
     */
    suspend fun hasCompletedWorkshopSetup(userId: String): Boolean
    suspend fun setWorkshopSetupCompleted(userId: String)

    /**
     * Per-user cache of "this account's remote profile already has workshop data", set by
     * the launch router after a one-shot Firestore check on a fresh install (the reinstall
     * case). Scoped to [userId] so confirming one account never lets a different account on
     * the same device skip setup.
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
     * Whether the user has dismissed (via ✕) or already acted on (tapped Join)
     * the Dashboard community banner. Device-wide, like the push-permission
     * flag — once set, the banner never shows again. The Settings row is
     * unaffected and remains the permanent entry point.
     */
    suspend fun hasDismissedCommunityBanner(): Boolean
    suspend fun setCommunityBannerDismissed()

    /** Debug-menu only: re-show the community banner by clearing the dismiss flag. */
    suspend fun clearCommunityBannerDismissed()

    /**
     * Whether the contextual "Watch how it works" tutorial for [topicId] has been seen or
     * dismissed on this device. Once set, the empty-state card collapses to a quiet link.
     * Device-wide (like the push/community flags) — keyed by [topicId] so each surface tracks
     * independently. [topicId] matches a
     * [TutorialTopic][com.danzucker.stitchpad.feature.tutorials.domain.model.TutorialTopic] id.
     */
    suspend fun hasSeenTutorial(topicId: String): Boolean
    suspend fun setTutorialSeen(topicId: String)

    /**
     * One-shot "this milestone's celebration has been shown" flag, per user per
     * milestone. Set at trigger time (not dismissal) so a crash mid-celebration
     * can never cause a re-show. [milestoneKey] is a
     * [Milestone.key][com.danzucker.stitchpad.core.presentation.celebration.Milestone].
     */
    suspend fun hasCelebrated(userId: String, milestoneKey: String): Boolean
    suspend fun setCelebrated(userId: String, milestoneKey: String)

    /** Debug-menu only: clears every celebration flag for every user. Idempotent. */
    suspend fun clearCelebrationsForDebug()

    /**
     * Resets all onboarding flags to false. Debug-menu use only — production
     * code should not call this. Idempotent.
     */
    suspend fun resetForDebug()
}
