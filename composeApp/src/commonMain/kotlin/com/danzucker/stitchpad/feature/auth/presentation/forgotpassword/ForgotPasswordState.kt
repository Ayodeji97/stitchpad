package com.danzucker.stitchpad.feature.auth.presentation.forgotpassword

import com.danzucker.stitchpad.core.presentation.UiText

data class ForgotPasswordState(
    val email: String = "",
    val emailError: UiText? = null,
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false
)
