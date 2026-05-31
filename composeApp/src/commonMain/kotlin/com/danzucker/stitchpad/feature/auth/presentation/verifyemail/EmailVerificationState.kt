package com.danzucker.stitchpad.feature.auth.presentation.verifyemail

data class EmailVerificationState(
    val email: String = "",
    val isChecking: Boolean = false,
    val isResending: Boolean = false,
    val resendCooldownSeconds: Int = 0,
)
