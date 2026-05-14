package com.danzucker.stitchpad.feature.settings.presentation.changepassword

sealed interface ChangePasswordAction {
    data class OnReauthPasswordChange(val value: String) : ChangePasswordAction
    data object OnReauthConfirm : ChangePasswordAction
    data object OnReauthDismiss : ChangePasswordAction
    data object OnForgotPassword : ChangePasswordAction
    data class OnNewPasswordChange(val value: String) : ChangePasswordAction
    data class OnConfirmPasswordChange(val value: String) : ChangePasswordAction
    data object OnSubmitClick : ChangePasswordAction
    data object OnBackClick : ChangePasswordAction
}
