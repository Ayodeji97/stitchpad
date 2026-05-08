package com.danzucker.stitchpad.feature.settings.presentation.changeemail

import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.SignInProvider
import org.jetbrains.compose.resources.StringResource

/**
 * Two-phase state machine: re-auth (bottom sheet) → email entry (main form).
 *
 * The sheet shows on entry and can't be dismissed without either confirming or
 * canceling out of the screen entirely. Once [isReauthenticated] flips true,
 * the sheet hides and the form is interactive.
 */
data class ChangeEmailState(
    val isLoading: Boolean = true,
    val currentEmail: String = "",
    val signInProvider: SignInProvider = SignInProvider.UNKNOWN,

    // Reauth phase
    val showReauthSheet: Boolean = true,
    val reauthPassword: String = "",
    val reauthError: UiText? = null,
    val isReauthenticating: Boolean = false,
    val isReauthenticated: Boolean = false,

    // Form phase
    val newEmail: String = "",
    val emailError: StringResource? = null,
    val isSubmitting: Boolean = false,
) {
    val canSubmit: Boolean
        get() = isReauthenticated &&
            !isSubmitting &&
            emailError == null &&
            newEmail.trim().isNotEmpty() &&
            newEmail.trim() != currentEmail
}
