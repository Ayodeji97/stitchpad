package com.danzucker.stitchpad.feature.auth.presentation.verifyemail

sealed interface EmailVerificationAction {
    data object OnCheckVerificationClick : EmailVerificationAction
    data object OnResendClick : EmailVerificationAction
    data object OnScreenResumed : EmailVerificationAction
    data object OnScreenPaused : EmailVerificationAction
    data object OnDebugSkipClick : EmailVerificationAction
    data object OnLogOutClick : EmailVerificationAction
}
