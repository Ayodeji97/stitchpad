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

    override suspend fun resetForDebug() {
        val editor = prefs.edit()
            .putBoolean(KEY_HAS_SEEN_ONBOARDING, false)
            .putBoolean(KEY_BYPASSED_EMAIL_VERIFICATION, false)
            .putBoolean(KEY_HAS_ASKED_PUSH_PERMISSION, false)
        // Per-user completed + confirmation keys aren't enumerable up front — clear by prefix.
        prefs.all.keys
            .filter {
                it.startsWith(KEY_COMPLETED_WORKSHOP_PREFIX) || it.startsWith(KEY_CONFIRMED_WORKSHOP_PREFIX)
            }
            .forEach { editor.remove(it) }
        editor.commit()
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
