package com.danzucker.stitchpad.feature.auth.presentation.signup

import com.danzucker.stitchpad.core.presentation.UiText

sealed interface SignUpEvent {
    data object NavigateToLogin : SignUpEvent
    data object NavigateToHome : SignUpEvent
    data class ShowError(val message: UiText) : SignUpEvent
}
