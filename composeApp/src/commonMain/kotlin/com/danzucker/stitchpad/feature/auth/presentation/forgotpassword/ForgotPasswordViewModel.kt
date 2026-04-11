package com.danzucker.stitchpad.feature.auth.presentation.forgotpassword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.AuthError
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

class ForgotPasswordViewModel(
    private val authRepository: AuthRepository,
    private val emailValidator: PatternValidator
) : ViewModel() {

    private val _state = MutableStateFlow(ForgotPasswordState())
    val state = _state.asStateFlow()

    private val _events = Channel<ForgotPasswordEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: ForgotPasswordAction) {
        when (action) {
            is ForgotPasswordAction.OnEmailChange -> {
                _state.update { it.copy(email = action.email, emailError = null) }
            }
            ForgotPasswordAction.OnEmailBlur -> {
                if (_state.value.email.isNotBlank()) validateEmail()
            }
            ForgotPasswordAction.OnSendClick -> sendResetEmail()
            ForgotPasswordAction.OnBackToLoginClick -> {
                viewModelScope.launch {
                    _events.send(ForgotPasswordEvent.NavigateToLogin)
                }
            }
        }
    }

    private fun validateEmail(): Boolean {
        if (!emailValidator.matches(_state.value.email)) {
            _state.update { it.copy(emailError = UiText.StringResourceText(Res.string.error_invalid_email)) }
            return false
        }
        return true
    }

    private fun sendResetEmail() {
        if (!validateEmail()) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val result = authRepository.sendPasswordResetEmail(_state.value.email)
                when (result) {
                    is Result.Success -> _state.update { it.copy(isSuccess = true) }
                    is Result.Error -> when (result.error) {
                        // Don't reveal whether the email exists (security best practice)
                        AuthError.USER_NOT_FOUND -> _state.update { it.copy(isSuccess = true) }
                        else -> _events.send(ForgotPasswordEvent.ShowError(result.error.toUiText()))
                    }
                }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }
}
