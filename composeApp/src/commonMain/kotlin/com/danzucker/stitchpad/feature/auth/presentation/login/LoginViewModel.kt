package com.danzucker.stitchpad.feature.auth.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.auth.domain.PatternValidator
import com.danzucker.stitchpad.feature.auth.presentation.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
            LoginAction.OnLoginClick -> login()
            LoginAction.OnSignUpClick -> {
                viewModelScope.launch {
                    _events.send(LoginEvent.NavigateToSignUp)
                }
            }
        }
    }

    private fun login() {
        val currentState = _state.value

        if (!emailValidator.matches(currentState.email)) {
            _state.update {
                it.copy(
                    emailError = com.danzucker.stitchpad.core.presentation.UiText
                        .DynamicString("Invalid email format"),
                )
            }
            return
        }
        if (currentState.password.length < 6) {
            _state.update {
                it.copy(
                    passwordError = com.danzucker.stitchpad.core.presentation.UiText
                        .DynamicString("Password must be at least 6 characters"),
                )
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = authRepository.signInWithEmail(currentState.email, currentState.password)
            _state.update { it.copy(isLoading = false) }

            when (result) {
                is Result.Success -> _events.send(LoginEvent.NavigateToHome)
                is Result.Error -> _events.send(LoginEvent.ShowError(result.error.toUiText()))
            }
        }
    }
}
