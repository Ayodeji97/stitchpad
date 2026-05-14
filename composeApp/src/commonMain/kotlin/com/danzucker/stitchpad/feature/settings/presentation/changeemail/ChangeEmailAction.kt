package com.danzucker.stitchpad.feature.settings.presentation.changeemail

sealed interface ChangeEmailAction {
    data class OnReauthPasswordChange(val value: String) : ChangeEmailAction
    data object OnReauthConfirm : ChangeEmailAction
    data object OnReauthDismiss : ChangeEmailAction
    data object OnForgotPassword : ChangeEmailAction
    data class OnNewEmailChange(val value: String) : ChangeEmailAction
    data object OnSubmitClick : ChangeEmailAction
    data object OnBackClick : ChangeEmailAction
}
