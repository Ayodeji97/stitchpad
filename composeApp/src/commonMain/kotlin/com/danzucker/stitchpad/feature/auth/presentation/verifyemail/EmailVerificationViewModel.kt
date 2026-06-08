package com.danzucker.stitchpad.feature.auth.presentation.verifyemail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.AuthError
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.auth.domain.SignOutUseCase
import com.danzucker.stitchpad.feature.auth.presentation.toUiText
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferencesStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.email_verify_not_yet_message
import stitchpad.composeapp.generated.resources.email_verify_sent_message

private const val POLL_INTERVAL_MS = 4000L
private const val RESEND_COOLDOWN_SECONDS = 60
private const val ONE_SECOND_MS = 1000L

class EmailVerificationViewModel(
    private val authRepository: AuthRepository,
    private val onboardingPreferences: OnboardingPreferencesStore,
    private val signOutUseCase: SignOutUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(EmailVerificationState())
    val state = _state.asStateFlow()

    private val _events = Channel<EmailVerificationEvent>()
    val events = _events.receiveAsFlow()

    private var pollingJob: Job? = null

    init {
        viewModelScope.launch {
            val email = authRepository.getCurrentUser()?.email.orEmpty()
            _state.update { it.copy(email = email) }
            // Send a fresh link on every entry to this route (signup, login, or
            // splash re-entry) so the on-screen "we sent a link" copy is always
            // accurate and the user never has to rely on an expired link.
            // Start the cooldown on success — and also when the server says we're
            // throttled (a recent send within its 60s window) — so the Resend
            // button reflects the real server state instead of looking enabled
            // and erroring on tap. On other failures (e.g. network) leave Resend
            // enabled so the user can retry.
            val result = authRepository.sendEmailVerification()
            val throttled = result is Result.Error && result.error == AuthError.TOO_MANY_REQUESTS
            if (result is Result.Success || throttled) {
                startCooldown()
            }
        }
    }

    fun onAction(action: EmailVerificationAction) {
        when (action) {
            EmailVerificationAction.OnScreenResumed -> onResumed()
            EmailVerificationAction.OnScreenPaused -> pollingJob?.cancel()
            EmailVerificationAction.OnCheckVerificationClick -> checkNow()
            EmailVerificationAction.OnResendClick -> resend()
            EmailVerificationAction.OnDebugSkipClick -> debugSkip()
            EmailVerificationAction.OnLogOutClick -> logOut()
        }
    }

    private fun onResumed() {
        viewModelScope.launch {
            if (checkVerified()) return@launch
            startPolling()
        }
    }

    private fun checkNow() {
        viewModelScope.launch {
            _state.update { it.copy(isChecking = true) }
            val reload = authRepository.reloadUser()
            _state.update { it.copy(isChecking = false) }
            when (reload) {
                // On an explicit user-triggered check, a failed reload is a real
                // error (offline / invalidated session) — surface it instead of
                // misreporting "not verified yet" off the stale cached value.
                is Result.Error -> _events.send(
                    EmailVerificationEvent.ShowError(reload.error.toUiText())
                )
                is Result.Success -> if (!emitIfVerified()) {
                    _events.send(
                        EmailVerificationEvent.ShowMessage(
                            UiText.StringResourceText(Res.string.email_verify_not_yet_message)
                        )
                    )
                }
            }
        }
    }

    /**
     * Reloads from the server and, if verified, emits NavigateToNext. Reload
     * failures are swallowed here — used by the background poll and on-resume
     * check, where a transient error should just be retried, not surfaced.
     */
    private suspend fun checkVerified(): Boolean {
        authRepository.reloadUser()
        return emitIfVerified()
    }

    private suspend fun emitIfVerified(): Boolean {
        return if (authRepository.isEmailVerified()) {
            pollingJob?.cancel()
            _events.send(EmailVerificationEvent.NavigateToNext)
            true
        } else {
            false
        }
    }

    private fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                if (checkVerified()) break
            }
        }
    }

    private fun resend() {
        if (_state.value.resendCooldownSeconds > 0 || _state.value.isResending) return
        viewModelScope.launch {
            _state.update { it.copy(isResending = true) }
            val result = authRepository.sendEmailVerification()
            _state.update { it.copy(isResending = false) }
            when (result) {
                is Result.Success -> {
                    startCooldown()
                    _events.send(
                        EmailVerificationEvent.ShowMessage(
                            UiText.StringResourceText(Res.string.email_verify_sent_message)
                        )
                    )
                }
                is Result.Error -> _events.send(
                    EmailVerificationEvent.ShowError(result.error.toUiText())
                )
            }
        }
    }

    private fun startCooldown() {
        viewModelScope.launch {
            _state.update { it.copy(resendCooldownSeconds = RESEND_COOLDOWN_SECONDS) }
            while (_state.value.resendCooldownSeconds > 0) {
                delay(ONE_SECOND_MS)
                _state.update { it.copy(resendCooldownSeconds = it.resendCooldownSeconds - 1) }
            }
        }
    }

    private fun debugSkip() {
        viewModelScope.launch {
            onboardingPreferences.setEmailVerificationBypassed()
            pollingJob?.cancel()
            _events.send(EmailVerificationEvent.NavigateToNext)
        }
    }

    private fun logOut() {
        viewModelScope.launch {
            pollingJob?.cancel()
            signOutUseCase()
            _events.send(EmailVerificationEvent.NavigateToLogin)
        }
    }
}
