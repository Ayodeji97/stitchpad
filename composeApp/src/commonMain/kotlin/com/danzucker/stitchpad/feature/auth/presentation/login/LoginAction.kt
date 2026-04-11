package com.danzucker.stitchpad.feature.auth.presentation.login

sealed interface LoginAction {
    data class OnEmailChange(val email: String) : LoginAction
    data class OnPasswordChange(val password: String) : LoginAction
    data object OnTogglePasswordVisibility : LoginAction
    data object OnEmailBlur : LoginAction
    data object OnPasswordBlur : LoginAction
    data object OnLoginClick : LoginAction
    data object OnSignUpClick : LoginAction
    data object OnForgotPasswordClick : LoginAction
}
