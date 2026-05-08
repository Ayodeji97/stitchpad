package com.danzucker.stitchpad.feature.settings.presentation.deleteaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.currentPlatformName
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.AuthError
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.auth.domain.SignInProvider
import com.danzucker.stitchpad.feature.auth.presentation.toUiText
import com.danzucker.stitchpad.feature.settings.domain.DeletionFeedback
import com.danzucker.stitchpad.feature.settings.domain.DeletionFeedbackRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.delete_account_failed
import stitchpad.composeapp.generated.resources.error_provider_not_supported
import stitchpad.composeapp.generated.resources.password_reset_email_sent

private const val TAG = "DeleteAccountVM"
private const val GOODBYE_DELAY_MS = 2_500L
private const val APP_VERSION = "1.0.0"
private const val LOCALE = "en"

@Suppress("TooManyFunctions")
class DeleteAccountViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val deletionFeedbackRepository: DeletionFeedbackRepository,
) : ViewModel() {

    private var hasLoaded = false
    private val _state = MutableStateFlow(DeleteAccountState())

    private val _events = Channel<DeleteAccountEvent>(Channel.BUFFERED)
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
            initialValue = DeleteAccountState(),
        )

    @Suppress("CyclomaticComplexMethod")
    fun onAction(action: DeleteAccountAction) {
        when (action) {
            DeleteAccountAction.OnConfirmContinue -> _state.update { it.copy(phase = DeletePhase.Reason) }
            DeleteAccountAction.OnConfirmCancel -> emit(DeleteAccountEvent.NavigateBack)

            is DeleteAccountAction.OnReasonSelect -> _state.update {
                it.copy(selectedReason = action.reason)
            }
            is DeleteAccountAction.OnAdditionalNotesChange -> _state.update {
                it.copy(additionalNotes = action.value.take(MAX_NOTES_LENGTH))
            }
            DeleteAccountAction.OnReasonContinue -> {
                if (_state.value.canContinueFromReason) {
                    _state.update { it.copy(phase = DeletePhase.Reauth) }
                }
            }
            DeleteAccountAction.OnReasonCancel -> emit(DeleteAccountEvent.NavigateBack)

            is DeleteAccountAction.OnReauthPasswordChange -> _state.update {
                it.copy(reauthPassword = action.value, reauthError = null)
            }
            DeleteAccountAction.OnReauthConfirm -> reauthAndDelete()
            DeleteAccountAction.OnReauthCancel -> emit(DeleteAccountEvent.NavigateBack)
            DeleteAccountAction.OnForgotPassword -> sendPasswordReset()

            DeleteAccountAction.OnGoodbyeContinue ->
                emit(DeleteAccountEvent.NavigateToLoginAfterDelete)

            DeleteAccountAction.OnBackClick -> emit(DeleteAccountEvent.NavigateBack)
        }
    }

    private fun loadAccount() {
        viewModelScope.launch {
            val authUser = authRepository.getCurrentUser()
            if (authUser == null) {
                emit(DeleteAccountEvent.NavigateBack)
                return@launch
            }
            _state.update {
                it.copy(
                    isLoading = false,
                    email = authUser.email,
                    signInProvider = authRepository.getSignInProvider(),
                    // plan + daysActive will be wired from EntitlementsRepository / users
                    // doc once the analytics surface needs them; for V1 we ship the
                    // anonymous payload with conservative defaults.
                    plan = "free",
                    daysActive = 0,
                )
            }
        }
    }

    private fun reauthAndDelete() {
        val current = _state.value
        if (current.signInProvider != SignInProvider.EMAIL_PASSWORD) {
            _state.update { it.copy(reauthError = providerNotSupported()) }
            return
        }
        if (current.reauthPassword.isBlank()) return

        viewModelScope.launch {
            _state.update { it.copy(isReauthenticating = true, reauthError = null) }
            val reauth = authRepository.reauthenticateWithPassword(current.reauthPassword)
            _state.update { it.copy(isReauthenticating = false) }
            when (reauth) {
                is Result.Success -> runDeletePipeline()
                is Result.Error -> _state.update { it.copy(reauthError = reauth.error.toUiText()) }
            }
        }
    }

    /**
     * Order matters: feedback first (we lose write permission once the auth user
     * is gone), then a best-effort client sweep of subcollections, then the
     * authoritative auth.delete(). The Cloud Function (onAuthUserDeleted) sweeps
     * any orphan documents the client missed.
     */
    private fun runDeletePipeline() {
        val current = _state.value
        viewModelScope.launch {
            _state.update { it.copy(phase = DeletePhase.Processing, reauthPassword = "") }

            val authUser = authRepository.getCurrentUser()
            if (authUser == null) {
                emit(DeleteAccountEvent.ShowSnackbar(deleteFailedText()))
                _state.update { it.copy(phase = DeletePhase.Reauth) }
                return@launch
            }

            // 1. Save anonymous feedback. Failures here log but never block the
            // user's right to delete their account.
            val feedback = DeletionFeedback(
                reason = current.selectedReason ?: return@launch,
                additionalNotes = current.additionalNotes.takeIf { it.isNotBlank() },
                plan = current.plan,
                daysActive = current.daysActive,
                platform = currentPlatformName,
                appVersion = APP_VERSION,
                locale = LOCALE,
            )
            runCatching { deletionFeedbackRepository.submitFeedback(feedback) }
                .onFailure { error ->
                    AppLogger.e(tag = TAG, throwable = error) { "submitFeedback threw" }
                }

            // 2. Best-effort client sweep of users/{uid} subcollections.
            runCatching { userRepository.deleteUserData(authUser.id) }
                .onFailure { error ->
                    AppLogger.e(tag = TAG, throwable = error) { "deleteUserData threw" }
                }

            // 3. Authoritative auth deletion. requires-recent-login flips us back
            // to the reauth sheet with a snackbar.
            when (val result = authRepository.deleteAccount()) {
                is Result.Success -> {
                    _state.update { it.copy(phase = DeletePhase.Goodbye) }
                    delay(GOODBYE_DELAY_MS)
                    emit(DeleteAccountEvent.NavigateToLoginAfterDelete)
                }
                is Result.Error -> {
                    AppLogger.e(tag = TAG) { "deleteAccount failed error=${result.error}" }
                    val nextPhase = if (result.error == AuthError.REQUIRES_RECENT_LOGIN) {
                        DeletePhase.Reauth
                    } else {
                        DeletePhase.Reauth
                    }
                    _state.update {
                        it.copy(phase = nextPhase, reauthError = result.error.toUiText())
                    }
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
                    DeleteAccountEvent.ShowSnackbar(
                        UiText.StringResourceText(Res.string.password_reset_email_sent)
                    )
                )
                is Result.Error -> emit(DeleteAccountEvent.ShowSnackbar(result.error.toUiText()))
            }
        }
    }

    private fun providerNotSupported(): UiText =
        UiText.StringResourceText(Res.string.error_provider_not_supported)

    private fun deleteFailedText(): UiText =
        UiText.StringResourceText(Res.string.delete_account_failed)

    private fun emit(event: DeleteAccountEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    companion object {
        private const val MAX_NOTES_LENGTH = 500
    }
}
