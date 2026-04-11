package com.danzucker.stitchpad.feature.auth.presentation.login

import com.danzucker.stitchpad.core.presentation.UiText

sealed interface LoginEvent {
    data object NavigateToSignUp : LoginEvent
    data object NavigateToForgotPassword : LoginEvent
    data object NavigateToHome : LoginEvent
    data class ShowError(val message: UiText) : LoginEvent
}
