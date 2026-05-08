package com.danzucker.stitchpad.feature.settings.presentation.changepassword

import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.SignInProvider
import org.jetbrains.compose.resources.StringResource

private const val MIN_PASSWORD_LENGTH = 8

data class ChangePasswordState(
    val isLoading: Boolean = true,
    val email: String = "",
    val signInProvider: SignInProvider = SignInProvider.UNKNOWN,

    // Reauth phase
    val showReauthSheet: Boolean = true,
    val reauthPassword: String = "",
    val reauthError: UiText? = null,
    val isReauthenticating: Boolean = false,
    val isReauthenticated: Boolean = false,

    // Form phase
    val newPassword: String = "",
    val confirmPassword: String = "",
    val newPasswordError: StringResource? = null,
    val confirmPasswordError: StringResource? = null,
    val isSubmitting: Boolean = false,
) {
    val canSubmit: Boolean
        get() = isReauthenticated &&
            !isSubmitting &&
            newPasswordError == null &&
            confirmPasswordError == null &&
            newPassword.length >= MIN_PASSWORD_LENGTH &&
            confirmPassword == newPassword
}
