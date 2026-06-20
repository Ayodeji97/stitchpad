package com.danzucker.stitchpad.feature.auth.presentation.login

import com.danzucker.stitchpad.core.presentation.UiText

sealed interface LoginEvent {
    data object NavigateToSignUp : LoginEvent
    data object NavigateToForgotPassword : LoginEvent

    /**
     * @param fromPasswordLogin true only for email/password sign-in, so the UI knows
     * to commit the typed credentials to autofill. False for SSO (nothing to save).
     */
    data class NavigateToHome(val fromPasswordLogin: Boolean) : LoginEvent
    data class ShowError(val message: UiText) : LoginEvent
    data object ShowComingSoon : LoginEvent
}
