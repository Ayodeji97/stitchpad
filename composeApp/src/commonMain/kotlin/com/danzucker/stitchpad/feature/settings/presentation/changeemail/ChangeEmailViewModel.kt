package com.danzucker.stitchpad.feature.settings.presentation.changeemail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.AuthError
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.auth.domain.PatternValidator
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
import stitchpad.composeapp.generated.resources.change_email_sent
import stitchpad.composeapp.generated.resources.error_invalid_email
import stitchpad.composeapp.generated.resources.error_provider_not_supported
import stitchpad.composeapp.generated.resources.password_reset_email_sent

private const val TAG = "ChangeEmailVM"

class ChangeEmailViewModel(
    private val authRepository: AuthRepository,
    private val emailValidator: PatternValidator,
) : ViewModel() {

    private var hasLoaded = false
    private val _state = MutableStateFlow(ChangeEmailState())

    private val _events = Channel<ChangeEmailEvent>(Channel.BUFFERED)
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
            initialValue = ChangeEmailState(),
        )

    fun onAction(action: ChangeEmailAction) {
        when (action) {
            is ChangeEmailAction.OnReauthPasswordChange -> _state.update {
                it.copy(reauthPassword = action.value, reauthError = null)
            }
            ChangeEmailAction.OnReauthConfirm -> reauthenticate()
            ChangeEmailAction.OnReauthDismiss -> {
                // Only treat dismiss as "user canceled" before reauth. If reauth
                // already succeeded, the sheet is being dismissed programmatically
                // (state.showReauthSheet flipped false), so don't navigate away.
                if (!_state.value.isReauthenticated) emit(ChangeEmailEvent.NavigateBack)
            }
            ChangeEmailAction.OnForgotPassword -> sendPasswordReset()
            is ChangeEmailAction.OnNewEmailChange -> _state.update {
                it.copy(newEmail = action.value, emailError = null)
            }
            ChangeEmailAction.OnSubmitClick -> submit()
            ChangeEmailAction.OnBackClick -> emit(ChangeEmailEvent.NavigateBack)
        }
    }

    private fun loadAccount() {
        viewModelScope.launch {
            val authUser = authRepository.getCurrentUser()
            if (authUser == null) {
                emit(ChangeEmailEvent.NavigateBack)
                return@launch
            }
            val provider = authRepository.getSignInProvider()
            _state.update {
                it.copy(
                    isLoading = false,
                    currentEmail = authUser.email,
                    signInProvider = provider,
                )
            }
        }
    }

    private fun reauthenticate() {
        val current = _state.value
        when (current.signInProvider) {
            SignInProvider.EMAIL_PASSWORD -> {
                if (current.reauthPassword.isBlank()) return
                runReauth { authRepository.reauthenticateWithPassword(current.reauthPassword) }
            }
            SignInProvider.APPLE -> runReauth { authRepository.reauthenticateWithApple() }
            SignInProvider.GOOGLE -> runReauth { authRepository.reauthenticateWithGoogle() }
            SignInProvider.UNKNOWN -> _state.update {
                it.copy(reauthError = current.signInProvider.toReauthErrorText())
            }
        }
    }

    private fun runReauth(block: suspend () -> EmptyResult<AuthError>) {
        viewModelScope.launch {
            _state.update { it.copy(isReauthenticating = true, reauthError = null) }
            val result = block()
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
        val email = _state.value.currentEmail
        if (email.isBlank()) return
        viewModelScope.launch {
            when (val result = authRepository.sendPasswordResetEmail(email)) {
                is Result.Success -> emit(
                    ChangeEmailEvent.ShowSnackbar(
                        UiText.StringResourceText(Res.string.password_reset_email_sent)
                    )
                )
                is Result.Error -> emit(ChangeEmailEvent.ShowSnackbar(result.error.toUiText()))
            }
        }
    }

    private fun submit() {
        val current = _state.value
        val newEmail = current.newEmail.trim()

        if (!emailValidator.matches(newEmail)) {
            _state.update { it.copy(emailError = Res.string.error_invalid_email) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            val result = authRepository.updateEmail(newEmail)
            _state.update { it.copy(isSubmitting = false) }
            when (result) {
                is Result.Success -> emit(
                    ChangeEmailEvent.SaveSucceeded(
                        UiText.StringResourceText(
                            Res.string.change_email_sent,
                            arrayOf(newEmail),
                        )
                    )
                )
                is Result.Error -> {
                    AppLogger.e(tag = TAG) { "updateEmail failed error=${result.error}" }
                    emit(ChangeEmailEvent.ShowSnackbar(result.error.toUiText()))
                }
            }
        }
    }

    private fun emit(event: ChangeEmailEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}

@Suppress("UnusedReceiverParameter")
private fun SignInProvider.toReauthErrorText(): UiText =
    UiText.StringResourceText(Res.string.error_provider_not_supported)
