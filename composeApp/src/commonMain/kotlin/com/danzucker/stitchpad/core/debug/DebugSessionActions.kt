package com.danzucker.stitchpad.core.debug

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferencesStore

sealed interface SessionActionResult {
    data object Success : SessionActionResult
    data class Failure(val reason: String) : SessionActionResult

    /** Test-account creds aren't filled in `debug-test-accounts.properties`. */
    data object ConfigurationMissing : SessionActionResult
}

class DebugSessionActions(
    private val authRepository: AuthRepository,
    private val onboardingPreferences: OnboardingPreferencesStore,
) {
    suspend fun resetOnboardingFlags() {
        onboardingPreferences.resetForDebug()
    }

    suspend fun signOut(): SessionActionResult = when (val r = authRepository.signOut()) {
        is Result.Success -> SessionActionResult.Success
        is Result.Error -> SessionActionResult.Failure(r.error.toString())
    }

    suspend fun switchAccount(email: String, password: String): SessionActionResult {
        if (email.isBlank() || password.isBlank()) return SessionActionResult.ConfigurationMissing
        authRepository.signOut()
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
