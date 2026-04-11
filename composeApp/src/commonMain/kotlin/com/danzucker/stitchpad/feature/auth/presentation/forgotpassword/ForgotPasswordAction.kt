package com.danzucker.stitchpad.feature.auth.presentation.forgotpassword

sealed interface ForgotPasswordAction {
    data class OnEmailChange(val email: String) : ForgotPasswordAction
    data object OnEmailBlur : ForgotPasswordAction
    data object OnSendClick : ForgotPasswordAction
    data object OnBackToLoginClick : ForgotPasswordAction
}
