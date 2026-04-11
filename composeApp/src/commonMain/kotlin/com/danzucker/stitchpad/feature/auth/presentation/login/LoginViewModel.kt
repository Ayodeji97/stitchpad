package com.danzucker.stitchpad.feature.auth.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.auth.domain.PatternValidator
import com.danzucker.stitchpad.feature.auth.presentation.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_invalid_email
import stitchpad.composeapp.generated.resources.error_password_too_short

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val emailValidator: PatternValidator
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state = _state.asStateFlow()

    private val _events = Channel<LoginEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: LoginAction) {
        when (action) {
            is LoginAction.OnEmailChange -> {
                _state.update { it.copy(email = action.email, emailError = null) }
            }
            is LoginAction.OnPasswordChange -> {
                _state.update { it.copy(password = action.password, passwordError = null) }
            }
            LoginAction.OnTogglePasswordVisibility -> {
                _state.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
            }
            LoginAction.OnEmailBlur -> {
                if (_state.value.email.isNotBlank()) validateEmail()
            }
            LoginAction.OnPasswordBlur -> {
                if (_state.value.password.isNotBlank()) validatePassword()
            }
            LoginAction.OnLoginClick -> login()
            LoginAction.OnSignUpClick -> {
                viewModelScope.launch {
                    _events.send(LoginEvent.NavigateToSignUp)
                }
            }
            LoginAction.OnForgotPasswordClick -> {
                viewModelScope.launch {
                    _events.send(LoginEvent.NavigateToForgotPassword)
                }
            }
        }
    }

    private fun validateEmail(): Boolean {
        if (!emailValidator.matches(_state.value.email)) {
            _state.update {
                it.copy(emailError = UiText.StringResourceText(Res.string.error_invalid_email))
            }
            return false
        }
        return true
    }

    private fun validatePassword(): Boolean {
        if (_state.value.password.length < 6) {
            _state.update {
                it.copy(passwordError = UiText.StringResourceText(Res.string.error_password_too_short))
            }
            return false
        }
        return true
    }

    private fun login() {
        val emailValid = validateEmail()
        val passwordValid = validatePassword()
        if (!emailValid || !passwordValid) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val result = authRepository.signInWithEmail(_state.value.email, _state.value.password)
                when (result) {
                    is Result.Success -> _events.send(LoginEvent.NavigateToHome)
                    is Result.Error -> _events.send(LoginEvent.ShowError(result.error.toUiText()))
                }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }
}
