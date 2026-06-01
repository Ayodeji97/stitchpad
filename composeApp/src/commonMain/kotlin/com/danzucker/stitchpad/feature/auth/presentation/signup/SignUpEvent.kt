package com.danzucker.stitchpad.feature.auth.presentation.signup

import com.danzucker.stitchpad.core.presentation.UiText

sealed interface SignUpEvent {
    data object NavigateToLogin : SignUpEvent

    /** Email/password sign-up succeeded — gate on email verification before Home. */
    data object NavigateToEmailVerification : SignUpEvent

    /** SSO sign-up succeeded — provider email is already verified, skip the gate. */
    data object NavigateToHome : SignUpEvent
    data class ShowError(val message: UiText) : SignUpEvent
    data class OpenUrl(val url: String) : SignUpEvent
}
