package com.danzucker.stitchpad.feature.settings.presentation.deleteaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.currentAppVersion
import com.danzucker.stitchpad.core.domain.currentPlatformName
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.AuthError
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.auth.domain.SignInProvider
import com.danzucker.stitchpad.feature.auth.presentation.toUiText
import com.danzucker.stitchpad.feature.settings.domain.DeletionFeedback
import com.danzucker.stitchpad.feature.settings.domain.DeletionFeedbackRepository
import kotlinx.coroutines.Job
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

// V1 product is English-only (per PRD). Locale here is a literal until the
// app supports multiple locales; revisit alongside the i18n rollout.
private const val LOCALE = "en"

@Suppress("TooManyFunctions")
class DeleteAccountViewModel(
    private val authRepository: AuthRepository,
    private val deletionFeedbackRepository: DeletionFeedbackRepository,
) : ViewModel() {

    private var hasLoaded = false
    private val _state = MutableStateFlow(DeleteAccountState())

    private val _events = Channel<DeleteAccountEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Cancellable timer for the auto-navigate after the Goodbye screen renders. */
    private var goodbyeJob: Job? = null
    private var goodbyeNavigated = false

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

            DeleteAccountAction.OnGoodbyeContinue -> {
                // Cancel the auto-navigate timer if the user tapped the CTA first.
                goodbyeJob?.cancel()
                navigateToLoginAfterDeleteOnce()
            }

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
                    // plan + daysActive will be wired from EntitlementsProvider / users
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
        when (current.signInProvider) {
            SignInProvider.EMAIL_PASSWORD -> {
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
            SignInProvider.APPLE -> reauthSso { authRepository.reauthenticateWithApple() }
            SignInProvider.GOOGLE -> reauthSso { authRepository.reauthenticateWithGoogle() }
            SignInProvider.UNKNOWN -> _state.update { it.copy(reauthError = providerNotSupported()) }
        }
    }

    private fun reauthSso(block: suspend () -> EmptyResult<AuthError>) {
        viewModelScope.launch {
            _state.update { it.copy(isReauthenticating = true, reauthError = null) }
            val reauth = block()
            _state.update { it.copy(isReauthenticating = false) }
            when (reauth) {
                is Result.Success -> runDeletePipeline()
                is Result.Error -> _state.update { it.copy(reauthError = reauth.error.toUiText()) }
            }
        }
    }

    /**
     * Order matters:
     * 1. Save anonymous feedback while we still have write permission.
     * 2. Auth delete — authoritative. If this fails, the user's data is still
     *    intact and they can retry; we deliberately do NOT sweep Firestore
     *    before this point, because a sweep + auth-delete-failure would leave
     *    the user signed in with no customers / measurements / orders.
     * 3. Cleanup of orphaned documents under users/{uid} is the
     *    onAuthUserDeleted Cloud Function's job (deferred to a follow-up PR).
     *    Until that lands, deleted users' subcollections persist as orphans —
     *    inaccessible (security rules deny without auth) but storage-billable.
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

            val reason = current.selectedReason
            if (reason == null) {
                // Defensive: the FSM shouldn't allow Reauth without a reason picked,
                // but if it ever does we don't want the user trapped on the
                // non-cancellable Processing dialog. Bounce them back to the reason
                // sheet with a snackbar.
                _state.update { it.copy(phase = DeletePhase.Reason) }
                emit(DeleteAccountEvent.ShowSnackbar(deleteFailedText()))
                return@launch
            }
            val feedback = DeletionFeedback(
                reason = reason,
                additionalNotes = current.additionalNotes.takeIf { it.isNotBlank() },
                plan = current.plan,
                daysActive = current.daysActive,
                platform = currentPlatformName,
                appVersion = currentAppVersion,
                locale = LOCALE,
            )
            runCatching { deletionFeedbackRepository.submitFeedback(feedback) }
                .onFailure { error ->
                    AppLogger.e(tag = TAG, throwable = error) { "submitFeedback threw" }
                }

            when (val result = authRepository.deleteAccount()) {
                is Result.Success -> {
                    _state.update { it.copy(phase = DeletePhase.Goodbye) }
                    // Auto-navigate after the user has had time to read the goodbye
                    // copy. Stored as a cancellable Job so OnGoodbyeContinue can
                    // pre-empt it without double-firing the navigation event.
                    goodbyeJob = viewModelScope.launch {
                        delay(GOODBYE_DELAY_MS)
                        navigateToLoginAfterDeleteOnce()
                    }
                }
                is Result.Error -> {
                    AppLogger.e(tag = TAG) { "deleteAccount failed error=${result.error}" }
                    _state.update {
                        it.copy(
                            phase = DeletePhase.Reauth,
                            reauthError = result.error.toUiText(),
                        )
                    }
                }
            }
        }
    }

    /** Idempotent dispatcher for the post-delete navigation. */
    private fun navigateToLoginAfterDeleteOnce() {
        if (goodbyeNavigated) return
        goodbyeNavigated = true
        emit(DeleteAccountEvent.NavigateToLoginAfterDelete)
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
