package com.danzucker.stitchpad.feature.onboarding.domain

import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferencesStore

/**
 * Decides whether the workshop-setup screen must be shown for a logged-in user.
 *
 * The fast path is the local "completed" flag. That flag is wiped when the app is
 * uninstalled, so an existing user who reinstalls and signs back in would wrongly be
 * sent to setup again. To avoid that, when the local flag is unset we fall back to a
 * one-shot read of the remote profile: if it already holds workshop data we treat setup
 * as done and heal the local flag so subsequent launches are instant and offline-safe.
 */
class ResolveNeedsWorkshopSetup(
    private val onboardingPreferences: OnboardingPreferencesStore,
    private val userRepository: UserRepository,
) {
    /** Returns true when the workshop-setup screen should be shown for [userId]. */
    suspend operator fun invoke(userId: String): Boolean {
        if (onboardingPreferences.hasCompletedWorkshopSetup()) return false

        val remoteHasWorkshop = userRepository.hasWorkshopProfile(userId)
        if (remoteHasWorkshop) {
            onboardingPreferences.setWorkshopSetupCompleted()
        }
        return !remoteHasWorkshop
    }
}
