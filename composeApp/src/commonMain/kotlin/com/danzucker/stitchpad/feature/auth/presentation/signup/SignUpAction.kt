package com.danzucker.stitchpad.feature.auth.presentation.signup

sealed interface SignUpAction {
    data class OnDisplayNameChange(val displayName: String) : SignUpAction
    data class OnEmailChange(val email: String) : SignUpAction
    data class OnPasswordChange(val password: String) : SignUpAction
    data class OnConfirmPasswordChange(val confirmPassword: String) : SignUpAction
    data object OnTogglePasswordVisibility : SignUpAction
    data object OnToggleConfirmPasswordVisibility : SignUpAction
    data object OnDisplayNameBlur : SignUpAction
    data object OnEmailBlur : SignUpAction
    data object OnPasswordBlur : SignUpAction
    data object OnConfirmPasswordBlur : SignUpAction
    data object OnSignUpClick : SignUpAction
    data object OnLoginClick : SignUpAction
    data object OnTermsToggle : SignUpAction
    data object OnTermsLinkClick : SignUpAction
    data object OnPrivacyLinkClick : SignUpAction
    data object OnGoogleSignInClick : SignUpAction
    data object OnAppleSignInClick : SignUpAction
}
