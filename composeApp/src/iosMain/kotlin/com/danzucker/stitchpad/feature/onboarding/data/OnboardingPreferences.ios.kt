package com.danzucker.stitchpad.feature.onboarding.data

import platform.Foundation.NSUserDefaults

actual class OnboardingPreferences : OnboardingPreferencesStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun hasSeenOnboarding(): Boolean {
        return defaults.boolForKey(KEY_HAS_SEEN_ONBOARDING)
    }

    override suspend fun setOnboardingSeen() {
        defaults.setBool(true, forKey = KEY_HAS_SEEN_ONBOARDING)
    }

    override suspend fun hasCompletedWorkshopSetup(userId: String): Boolean {
        return defaults.boolForKey(completedWorkshopKey(userId))
    }

    override suspend fun setWorkshopSetupCompleted(userId: String) {
        defaults.setBool(true, forKey = completedWorkshopKey(userId))
    }

    override suspend fun hasConfirmedRemoteWorkshopProfile(userId: String): Boolean {
        return defaults.boolForKey(confirmedWorkshopKey(userId))
    }

    override suspend fun setConfirmedRemoteWorkshopProfile(userId: String) {
        defaults.setBool(true, forKey = confirmedWorkshopKey(userId))
    }

    override suspend fun hasBypassedEmailVerification(): Boolean {
        return defaults.boolForKey(KEY_BYPASSED_EMAIL_VERIFICATION)
    }

    override suspend fun setEmailVerificationBypassed() {
        defaults.setBool(true, forKey = KEY_BYPASSED_EMAIL_VERIFICATION)
    }

    override suspend fun hasAskedPushPermission(): Boolean {
        return defaults.boolForKey(KEY_HAS_ASKED_PUSH_PERMISSION)
    }

    override suspend fun setAskedPushPermission() {
        defaults.setBool(true, forKey = KEY_HAS_ASKED_PUSH_PERMISSION)
    }

    override suspend fun resetForDebug() {
        defaults.setBool(false, forKey = KEY_HAS_SEEN_ONBOARDING)
        defaults.setBool(false, forKey = KEY_BYPASSED_EMAIL_VERIFICATION)
        defaults.setBool(false, forKey = KEY_HAS_ASKED_PUSH_PERMISSION)
        // Per-user completed + confirmation keys aren't enumerable up front — clear by prefix.
        defaults.dictionaryRepresentation().keys
            .filterIsInstance<String>()
            .filter {
                it.startsWith(KEY_COMPLETED_WORKSHOP_PREFIX) || it.startsWith(KEY_CONFIRMED_WORKSHOP_PREFIX)
            }
            .forEach { defaults.removeObjectForKey(it) }
    }

    private fun completedWorkshopKey(userId: String): String =
        "$KEY_COMPLETED_WORKSHOP_PREFIX$userId"

    private fun confirmedWorkshopKey(userId: String): String =
        "$KEY_CONFIRMED_WORKSHOP_PREFIX$userId"

    companion object {
        private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
        private const val KEY_COMPLETED_WORKSHOP_PREFIX = "completed_workshop_setup_"
        private const val KEY_CONFIRMED_WORKSHOP_PREFIX = "confirmed_workshop_profile_"
        private const val KEY_BYPASSED_EMAIL_VERIFICATION = "bypassed_email_verification"
        private const val KEY_HAS_ASKED_PUSH_PERMISSION = "has_asked_push_permission"
    }
}
