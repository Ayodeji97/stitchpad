package com.danzucker.stitchpad.feature.onboarding.domain

import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferencesStore

/**
 * Decides whether the workshop-setup screen must be shown for a logged-in user.
 *
 * The fast path is the per-user "completed" flag (set when THIS user finishes/skips setup
 * on this install) or the per-user "remote-confirmed" flag. Both are wiped on uninstall, so
 * an existing user who reinstalls and signs back in would wrongly be sent to setup again. To
 * avoid that, when neither flag is set we fall back to a one-shot read of the remote
 * profile: if it already holds workshop data we treat setup as done and heal the per-user
 * flag so subsequent launches are instant and offline-safe.
 *
 * Every flag is scoped to [userId] — never device-wide — so one account finishing setup
 * never lets a different account on the same device skip its own setup.
 */
class ResolveNeedsWorkshopSetup(
    private val onboardingPreferences: OnboardingPreferencesStore,
    private val userRepository: UserRepository,
) {
    /** Returns true when the workshop-setup screen should be shown for [userId]. */
    suspend operator fun invoke(userId: String): Boolean {
        val alreadyDone = onboardingPreferences.hasCompletedWorkshopSetup(userId) ||
            onboardingPreferences.hasConfirmedRemoteWorkshopProfile(userId)
        if (alreadyDone) return false

        val remoteHasWorkshop = userRepository.hasWorkshopProfile(userId)
        if (remoteHasWorkshop) {
            onboardingPreferences.setConfirmedRemoteWorkshopProfile(userId)
        }
        return !remoteHasWorkshop
    }
}
