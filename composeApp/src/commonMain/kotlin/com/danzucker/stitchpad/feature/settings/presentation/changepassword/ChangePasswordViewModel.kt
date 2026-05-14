package com.danzucker.stitchpad.feature.settings.presentation.changepassword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.auth.domain.SignInProvider
import com.danzucker.stitchpad.feature.auth.presentation.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.change_password_updated
import stitchpad.composeapp.generated.resources.error_password_too_short
import stitchpad.composeapp.generated.resources.error_passwords_mismatch
import stitchpad.composeapp.generated.resources.error_provider_not_supported
import stitchpad.composeapp.generated.resources.password_reset_email_sent

private const val TAG = "ChangePasswordVM"
private const val MIN_PASSWORD_LENGTH = 6

class ChangePasswordViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private var hasLoaded = false
    private val _state = MutableStateFlow(ChangePasswordState())

    private val _events = Channel<ChangePasswordEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasLoaded) {
                hasLoaded = true
                loadAccount()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = ChangePasswordState(),
        )

    @Suppress("CyclomaticComplexMethod")
    fun onAction(action: ChangePasswordAction) {
        when (action) {
            is ChangePasswordAction.OnReauthPasswordChange -> _state.update {
                it.copy(reauthPassword = action.value, reauthError = null)
            }
            ChangePasswordAction.OnReauthConfirm -> reauthenticate()
            ChangePasswordAction.OnReauthDismiss -> {
                // See ChangeEmailViewModel for the same guard rationale: real
                // cancels only happen when reauth is neither in-flight nor
                // already succeeded.
                val s = _state.value
                if (!s.isReauthenticated && !s.isReauthenticating) {
                    emit(ChangePasswordEvent.NavigateBack)
                }
            }
            ChangePasswordAction.OnForgotPassword -> sendPasswordReset()
            is ChangePasswordAction.OnNewPasswordChange -> _state.update {
                it.copy(
                    newPassword = action.value,
                    newPasswordError = null,
                    // Re-validate confirm if it's already filled in.
                    confirmPasswordError = if (
                        it.confirmPassword.isNotEmpty() && it.confirmPassword != action.value
                    ) {
                        Res.string.error_passwords_mismatch
                    } else {
                        null
                    },
                )
            }
            is ChangePasswordAction.OnConfirmPasswordChange -> _state.update {
                it.copy(
                    confirmPassword = action.value,
                    confirmPasswordError = if (
                        action.value.isNotEmpty() && action.value != it.newPassword
                    ) {
                        Res.string.error_passwords_mismatch
                    } else {
                        null
                    },
                )
            }
            ChangePasswordAction.OnSubmitClick -> submit()
            ChangePasswordAction.OnBackClick -> emit(ChangePasswordEvent.NavigateBack)
        }
    }

    private fun loadAccount() {
        viewModelScope.launch {
            val authUser = authRepository.getCurrentUser()
            if (authUser == null) {
                emit(ChangePasswordEvent.NavigateBack)
                return@launch
            }
            val provider = authRepository.getSignInProvider()
            if (provider != SignInProvider.EMAIL_PASSWORD) {
                // Apple/Google providers don't have a password to change.
                // The Settings home hides this row for them; defensive guard
                // here covers a deep link.
                emit(
                    ChangePasswordEvent.ShowSnackbar(
                        UiText.StringResourceText(Res.string.error_provider_not_supported)
                    )
                )
                emit(ChangePasswordEvent.NavigateBack)
                return@launch
            }
            _state.update {
                it.copy(
                    isLoading = false,
                    email = authUser.email,
                    signInProvider = provider,
                )
            }
        }
    }

    private fun reauthenticate() {
        val current = _state.value
        if (current.reauthPassword.isBlank()) return

        viewModelScope.launch {
            _state.update { it.copy(isReauthenticating = true, reauthError = null) }
            val result = authRepository.reauthenticateWithPassword(current.reauthPassword)
            _state.update { it.copy(isReauthenticating = false) }
            when (result) {
                is Result.Success -> _state.update {
                    it.copy(
                        showReauthSheet = false,
                        isReauthenticated = true,
                        reauthPassword = "",
                    )
                }
                is Result.Error -> _state.update {
                    it.copy(reauthError = result.error.toUiText())
                }
            }
        }
    }

    private fun sendPasswordReset() {
        val email = _state.value.email
        if (email.isBlank()) return
        viewModelScope.launch {
            when (val result = authRepository.sendPasswordResetEmail(email)) {
                is Result.Success -> emit(
                    ChangePasswordEvent.ShowSnackbar(
                        UiText.StringResourceText(Res.string.password_reset_email_sent)
                    )
                )
                is Result.Error -> emit(ChangePasswordEvent.ShowSnackbar(result.error.toUiText()))
            }
        }
    }

    private fun submit() {
        val current = _state.value

        val newPwError = when {
            current.newPassword.length < MIN_PASSWORD_LENGTH ->
                Res.string.error_password_too_short
            else -> null
        }
        val confirmError = when {
            current.confirmPassword != current.newPassword ->
                Res.string.error_passwords_mismatch
            else -> null
        }
        if (newPwError != null || confirmError != null) {
            _state.update {
                it.copy(
                    newPasswordError = newPwError,
                    confirmPasswordError = confirmError,
                )
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            val result = authRepository.updatePassword(current.newPassword)
            _state.update { it.copy(isSubmitting = false) }
            when (result) {
                is Result.Success -> emit(
                    ChangePasswordEvent.SaveSucceeded(
                        UiText.StringResourceText(Res.string.change_password_updated)
                    )
                )
                is Result.Error -> {
                    AppLogger.e(tag = TAG) { "updatePassword failed error=${result.error}" }
                    emit(ChangePasswordEvent.ShowSnackbar(result.error.toUiText()))
                }
            }
        }
    }

    private fun emit(event: ChangePasswordEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}
