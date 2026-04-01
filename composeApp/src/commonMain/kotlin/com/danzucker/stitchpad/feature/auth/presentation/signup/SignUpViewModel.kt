package com.danzucker.stitchpad.feature.auth.presentation.signup

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

class SignUpViewModel(
    private val authRepository: AuthRepository,
    private val emailValidator: PatternValidator
) : ViewModel() {

    private val _state = MutableStateFlow(SignUpState())
    val state = _state.asStateFlow()

    private val _events = Channel<SignUpEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: SignUpAction) {
        when (action) {
            is SignUpAction.OnDisplayNameChange -> {
                _state.update { it.copy(displayName = action.displayName, displayNameError = null) }
            }
            is SignUpAction.OnEmailChange -> {
                _state.update { it.copy(email = action.email, emailError = null) }
            }
            is SignUpAction.OnPasswordChange -> {
                _state.update { it.copy(password = action.password, passwordError = null) }
            }
            is SignUpAction.OnConfirmPasswordChange -> {
                _state.update { it.copy(confirmPassword = action.confirmPassword, confirmPasswordError = null) }
            }
            SignUpAction.OnTogglePasswordVisibility -> {
                _state.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
            }
            SignUpAction.OnSignUpClick -> signUp()
            SignUpAction.OnLoginClick -> {
                viewModelScope.launch {
                    _events.send(SignUpEvent.NavigateToLogin)
                }
            }
        }
    }

    private fun signUp() {
        val currentState = _state.value
        var hasError = false

        if (currentState.displayName.isBlank()) {
            _state.update { it.copy(displayNameError = UiText.DynamicString("Name is required")) }
            hasError = true
        }
        if (!emailValidator.matches(currentState.email)) {
            _state.update { it.copy(emailError = UiText.DynamicString("Invalid email format")) }
            hasError = true
        }
        if (currentState.password.length < 6) {
            _state.update { it.copy(passwordError = UiText.DynamicString("Password must be at least 6 characters")) }
            hasError = true
        }
        if (currentState.password != currentState.confirmPassword) {
            _state.update { it.copy(confirmPasswordError = UiText.DynamicString("Passwords do not match")) }
            hasError = true
        }
        if (hasError) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = authRepository.signUpWithEmail(
                currentState.email,
                currentState.password,
                currentState.displayName
            )
            _state.update { it.copy(isLoading = false) }

            when (result) {
                is Result.Success -> _events.send(SignUpEvent.NavigateToHome)
                is Result.Error -> _events.send(SignUpEvent.ShowError(result.error.toUiText()))
            }
        }
    }
}
