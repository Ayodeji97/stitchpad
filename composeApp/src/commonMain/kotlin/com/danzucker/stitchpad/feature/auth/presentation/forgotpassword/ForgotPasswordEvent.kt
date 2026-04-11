package com.danzucker.stitchpad.feature.auth.presentation.forgotpassword

import com.danzucker.stitchpad.core.presentation.UiText

sealed interface ForgotPasswordEvent {
    data object NavigateToLogin : ForgotPasswordEvent
    data class ShowError(val message: UiText) : ForgotPasswordEvent
}
