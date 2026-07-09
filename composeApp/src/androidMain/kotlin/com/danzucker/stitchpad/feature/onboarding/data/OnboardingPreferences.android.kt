@file:Suppress("TooManyFunctions")

package com.danzucker.stitchpad.feature.onboarding.data

import android.content.Context
import android.content.SharedPreferences

actual class OnboardingPreferences(context: Context) : OnboardingPreferencesStore {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "stitchpad_prefs",
        Context.MODE_PRIVATE
    )

    override suspend fun hasSeenOnboarding(): Boolean {
        return prefs.getBoolean(KEY_HAS_SEEN_ONBOARDING, false)
    }

    override suspend fun setOnboardingSeen() {
        prefs.edit().putBoolean(KEY_HAS_SEEN_ONBOARDING, true).apply()
    }

    override suspend fun hasCompletedWorkshopSetup(userId: String): Boolean {
        return prefs.getBoolean(completedWorkshopKey(userId), false)
    }

    override suspend fun setWorkshopSetupCompleted(userId: String) {
        prefs.edit().putBoolean(completedWorkshopKey(userId), true).apply()
    }

    override suspend fun hasConfirmedRemoteWorkshopProfile(userId: String): Boolean {
        return prefs.getBoolean(confirmedWorkshopKey(userId), false)
    }

    override suspend fun setConfirmedRemoteWorkshopProfile(userId: String) {
        prefs.edit().putBoolean(confirmedWorkshopKey(userId), true).apply()
    }

    override suspend fun hasBypassedEmailVerification(): Boolean {
        return prefs.getBoolean(KEY_BYPASSED_EMAIL_VERIFICATION, false)
    }

    override suspend fun setEmailVerificationBypassed() {
        prefs.edit().putBoolean(KEY_BYPASSED_EMAIL_VERIFICATION, true).apply()
    }

    override suspend fun hasAskedPushPermission(): Boolean {
        return prefs.getBoolean(KEY_HAS_ASKED_PUSH_PERMISSION, false)
    }

    override suspend fun setAskedPushPermission() {
        prefs.edit().putBoolean(KEY_HAS_ASKED_PUSH_PERMISSION, true).apply()
    }

    override suspend fun hasDismissedCommunityBanner(): Boolean {
        return prefs.getBoolean(KEY_DISMISSED_COMMUNITY_BANNER, false)
    }

    override suspend fun setCommunityBannerDismissed() {
        prefs.edit().putBoolean(KEY_DISMISSED_COMMUNITY_BANNER, true).apply()
    }

    override suspend fun clearCommunityBannerDismissed() {
        prefs.edit().putBoolean(KEY_DISMISSED_COMMUNITY_BANNER, false).apply()
    }

    override suspend fun hasSeenTutorial(topicId: String): Boolean {
        return prefs.getBoolean(tutorialSeenKey(topicId), false)
    }

    override suspend fun setTutorialSeen(topicId: String) {
        prefs.edit().putBoolean(tutorialSeenKey(topicId), true).apply()
    }

    override suspend fun hasCelebrated(userId: String, milestoneKey: String): Boolean {
        return prefs.getBoolean(celebratedKey(userId, milestoneKey), false)
    }

    override suspend fun setCelebrated(userId: String, milestoneKey: String) {
        prefs.edit().putBoolean(celebratedKey(userId, milestoneKey), true).apply()
    }

    override suspend fun clearCelebrationsForDebug() {
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith(KEY_CELEBRATED_PREFIX) }
            .forEach { editor.remove(it) }
        editor.commit()
    }

    override suspend fun resetForDebug() {
        val editor = prefs.edit()
            .putBoolean(KEY_HAS_SEEN_ONBOARDING, false)
            .putBoolean(KEY_BYPASSED_EMAIL_VERIFICATION, false)
            .putBoolean(KEY_HAS_ASKED_PUSH_PERMISSION, false)
            .putBoolean(KEY_DISMISSED_COMMUNITY_BANNER, false)
        // Per-user/per-topic keys aren't enumerable up front — clear by prefix.
        prefs.all.keys
            .filter {
                it.startsWith(KEY_COMPLETED_WORKSHOP_PREFIX) ||
                    it.startsWith(KEY_CONFIRMED_WORKSHOP_PREFIX) ||
                    it.startsWith(KEY_TUTORIAL_SEEN_PREFIX) ||
                    it.startsWith(KEY_CELEBRATED_PREFIX)
            }
            .forEach { editor.remove(it) }
        editor.commit()
    }

    private fun completedWorkshopKey(userId: String): String =
        "$KEY_COMPLETED_WORKSHOP_PREFIX$userId"

    private fun confirmedWorkshopKey(userId: String): String =
        "$KEY_CONFIRMED_WORKSHOP_PREFIX$userId"

    private fun tutorialSeenKey(topicId: String): String =
        "$KEY_TUTORIAL_SEEN_PREFIX$topicId"

    private fun celebratedKey(userId: String, milestoneKey: String): String =
        "$KEY_CELEBRATED_PREFIX${milestoneKey}_$userId"

    companion object {
        private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
        private const val KEY_COMPLETED_WORKSHOP_PREFIX = "completed_workshop_setup_"
        private const val KEY_CONFIRMED_WORKSHOP_PREFIX = "confirmed_workshop_profile_"
        private const val KEY_BYPASSED_EMAIL_VERIFICATION = "bypassed_email_verification"
        private const val KEY_HAS_ASKED_PUSH_PERMISSION = "has_asked_push_permission"
        private const val KEY_DISMISSED_COMMUNITY_BANNER = "dismissed_community_banner"
        private const val KEY_TUTORIAL_SEEN_PREFIX = "tutorial_seen_"
        private const val KEY_CELEBRATED_PREFIX = "celebrated_"
    }
}
