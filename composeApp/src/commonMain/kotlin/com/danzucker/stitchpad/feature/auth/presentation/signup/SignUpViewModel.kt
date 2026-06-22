package com.danzucker.stitchpad.feature.auth.presentation.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.analytics.domain.Analytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.legal.LegalUrls
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
import stitchpad.composeapp.generated.resources.error_name_invalid_chars
import stitchpad.composeapp.generated.resources.error_name_required
import stitchpad.composeapp.generated.resources.error_name_too_short
import stitchpad.composeapp.generated.resources.error_password_too_short
import stitchpad.composeapp.generated.resources.error_passwords_mismatch

class SignUpViewModel(
    private val authRepository: AuthRepository,
    private val emailValidator: PatternValidator,
    private val analytics: Analytics,
) : ViewModel() {

    private val _state = MutableStateFlow(SignUpState())
    val state = _state.asStateFlow()

    private val _events = Channel<SignUpEvent>()
    val events = _events.receiveAsFlow()

    private val namePattern = Regex("^[\\p{L} '\\-]+$")

    @Suppress("CyclomaticComplexMethod")
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
            SignUpAction.OnToggleConfirmPasswordVisibility -> {
                _state.update { it.copy(isConfirmPasswordVisible = !it.isConfirmPasswordVisible) }
            }
            SignUpAction.OnDisplayNameBlur -> {
                if (_state.value.displayName.isNotBlank()) validateDisplayName()
            }
            SignUpAction.OnEmailBlur -> {
                if (_state.value.email.isNotBlank()) validateEmail()
            }
            SignUpAction.OnPasswordBlur -> {
                if (_state.value.password.isNotBlank()) validatePassword()
            }
            SignUpAction.OnConfirmPasswordBlur -> {
                if (_state.value.confirmPassword.isNotBlank()) validateConfirmPassword()
            }
            SignUpAction.OnSignUpClick -> signUp()
            SignUpAction.OnLoginClick -> {
                viewModelScope.launch {
                    _events.send(SignUpEvent.NavigateToLogin)
                }
            }
            SignUpAction.OnTermsToggle -> {
                _state.update { it.copy(acceptedTerms = !it.acceptedTerms) }
            }
            SignUpAction.OnTermsLinkClick -> {
                viewModelScope.launch { _events.send(SignUpEvent.OpenUrl(LegalUrls.TERMS)) }
            }
            SignUpAction.OnPrivacyLinkClick -> {
                viewModelScope.launch { _events.send(SignUpEvent.OpenUrl(LegalUrls.PRIVACY)) }
            }
            SignUpAction.OnAppleSignInClick -> appleSignIn()
            SignUpAction.OnGoogleSignInClick -> googleSignIn()
        }
    }

    private fun googleSignIn() {
        viewModelScope.launch {
            _state.update { it.copy(isSsoLoading = true) }
            try {
                when (val result = authRepository.signInWithGoogle()) {
                    is Result.Success -> _events.send(SignUpEvent.NavigateToHome)
                    is Result.Error -> {
                        if (result.error != AuthError.SSO_CANCELLED) {
                            _events.send(SignUpEvent.ShowError(result.error.toUiText()))
                        }
                    }
                }
            } finally {
                _state.update { it.copy(isSsoLoading = false) }
            }
        }
    }

    private fun appleSignIn() {
        viewModelScope.launch {
            _state.update { it.copy(isSsoLoading = true) }
            try {
                when (val result = authRepository.signInWithApple()) {
                    is Result.Success -> _events.send(SignUpEvent.NavigateToHome)
                    is Result.Error -> {
                        if (result.error != AuthError.SSO_CANCELLED) {
                            _events.send(SignUpEvent.ShowError(result.error.toUiText()))
                        }
                    }
                }
            } finally {
                _state.update { it.copy(isSsoLoading = false) }
            }
        }
    }

    private fun validateDisplayName(): Boolean {
        val name = _state.value.displayName
        return when {
            name.isBlank() -> {
                _state.update {
                    it.copy(displayNameError = UiText.StringResourceText(Res.string.error_name_required))
                }
                false
            }
            name.trim().length < 2 -> {
                _state.update {
                    it.copy(displayNameError = UiText.StringResourceText(Res.string.error_name_too_short))
                }
                false
            }
            !namePattern.matches(name.trim()) -> {
                _state.update {
                    it.copy(displayNameError = UiText.StringResourceText(Res.string.error_name_invalid_chars))
                }
                false
            }
            else -> true
        }
    }

    private fun validateEmail(): Boolean {
        if (!emailValidator.matches(_state.value.email)) {
            _state.update { it.copy(emailError = UiText.StringResourceText(Res.string.error_invalid_email)) }
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

    private fun validateConfirmPassword(): Boolean {
        if (_state.value.password != _state.value.confirmPassword) {
            _state.update {
                it.copy(
                    confirmPasswordError = UiText.StringResourceText(Res.string.error_passwords_mismatch)
                )
            }
            return false
        }
        return true
    }

    private fun signUp() {
        if (!_state.value.acceptedTerms) return
        val results = listOf(validateDisplayName(), validateEmail(), validatePassword(), validateConfirmPassword())
        if (results.any { !it }) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val result = authRepository.signUpWithEmail(
                    _state.value.email,
                    _state.value.password,
                    _state.value.displayName
                )
                when (result) {
                    // The verify screen sends the verification email on entry,
                    // so the same path serves signup, login and splash re-entry.
                    is Result.Success -> {
                        analytics.logEvent(AnalyticsEvent.SignUp)
                        _events.send(SignUpEvent.NavigateToEmailVerification)
                    }
                    is Result.Error -> _events.send(SignUpEvent.ShowError(result.error.toUiText()))
                }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }
}
