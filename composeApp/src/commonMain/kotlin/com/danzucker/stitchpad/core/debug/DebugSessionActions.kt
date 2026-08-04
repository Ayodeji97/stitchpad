package com.danzucker.stitchpad.core.debug

import com.danzucker.stitchpad.core.config.domain.CommunityBannerDismissal
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.auth.domain.SignOutUseCase
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferencesStore
import com.danzucker.stitchpad.feature.review.data.ReviewPreferencesStore
import com.danzucker.stitchpad.feature.review.presentation.ReviewController

sealed interface SessionActionResult {
    data object Success : SessionActionResult
    data class Failure(val reason: String) : SessionActionResult

    /** Test-account creds aren't filled in `debug-test-accounts.properties`. */
    data object ConfigurationMissing : SessionActionResult
}

class DebugSessionActions(
    private val authRepository: AuthRepository,
    private val onboardingPreferences: OnboardingPreferencesStore,
    private val signOutUseCase: SignOutUseCase,
    private val communityBannerDismissal: CommunityBannerDismissal,
    private val reviewController: ReviewController,
    private val reviewPreferences: ReviewPreferencesStore,
) {
    suspend fun resetOnboardingFlags() {
        onboardingPreferences.resetForDebug()
    }

    suspend fun clearCommunityBannerDismissed() {
        communityBannerDismissal.reset()
    }

    suspend fun resetCelebrations() {
        onboardingPreferences.clearCelebrationsForDebug()
    }

    fun forceReviewPrompt() {
        reviewController.forceArmForDebug()
    }

    suspend fun resetReviewSignals() {
        reviewPreferences.resetForDebug()
    }

    suspend fun signOut(): SessionActionResult = when (val r = signOutUseCase()) {
        is Result.Success -> SessionActionResult.Success
        is Result.Error -> SessionActionResult.Failure(r.error.toString())
    }

    suspend fun switchAccount(email: String, password: String): SessionActionResult {
        if (email.isBlank() || password.isBlank()) return SessionActionResult.ConfigurationMissing
        signOutUseCase()
        return when (val r = authRepository.signInWithEmail(email, password)) {
            is Result.Success -> SessionActionResult.Success
            is Result.Error -> SessionActionResult.Failure(r.error.toString())
        }
    }

    suspend fun deleteCurrentAccount(): SessionActionResult =
        when (val r = authRepository.deleteAccount()) {
            is Result.Success -> SessionActionResult.Success
            is Result.Error -> SessionActionResult.Failure(r.error.toString())
        }
}
