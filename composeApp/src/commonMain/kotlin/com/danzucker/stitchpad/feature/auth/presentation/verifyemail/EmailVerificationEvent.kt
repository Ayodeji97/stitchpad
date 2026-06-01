package com.danzucker.stitchpad.feature.auth.presentation.verifyemail

import com.danzucker.stitchpad.core.presentation.UiText

sealed interface EmailVerificationEvent {
    /** Email verified (or debug-skipped) — proceed past the gate. */
    data object NavigateToNext : EmailVerificationEvent
    data object NavigateToLogin : EmailVerificationEvent
    data class ShowError(val message: UiText) : EmailVerificationEvent
    data class ShowMessage(val message: UiText) : EmailVerificationEvent
}
