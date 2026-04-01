package com.danzucker.stitchpad.feature.auth.presentation.login

import com.danzucker.stitchpad.core.presentation.UiText

data class LoginState(
    val email: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val emailError: UiText? = null,
    val passwordError: UiText? = null
)
