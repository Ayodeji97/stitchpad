package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.core.sharing.applyImpliedNigerianCountryCode
import com.danzucker.stitchpad.core.sharing.normaliseNigerianPhone
import com.danzucker.stitchpad.core.sharing.validateNigerianMobileE164
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.branding.domain.BrandLogoError
import com.danzucker.stitchpad.feature.branding.domain.BrandLogoValidator
import com.danzucker.stitchpad.feature.branding.presentation.LogoUploadState
import com.danzucker.stitchpad.feature.branding.presentation.toUiText
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferencesStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_business_name_too_short
import stitchpad.composeapp.generated.resources.error_session_expired
import stitchpad.composeapp.generated.resources.error_unknown
import stitchpad.composeapp.generated.resources.error_whatsapp_invalid
import stitchpad.composeapp.generated.resources.workshop_logo_uploaded

class WorkshopSetupViewModel(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val onboardingPreferences: OnboardingPreferencesStore,
    private val logoValidator: BrandLogoValidator = BrandLogoValidator(),
) : ViewModel() {

    private val _state = MutableStateFlow(WorkshopSetupState())
    val state = _state.asStateFlow()

    private val _events = Channel<WorkshopSetupEvent>()
    val events = _events.receiveAsFlow()

    private var logoUploadJob: Job? = null

    fun onAction(action: WorkshopSetupAction) {
        when (action) {
            is WorkshopSetupAction.OnBusinessNameChange ->
                _state.update { it.copy(businessName = action.name, businessNameError = null) }
            is WorkshopSetupAction.OnWhatsAppNumberChange ->
                _state.update { it.copy(whatsappNumber = capWhatsAppDigits(action.raw), whatsappError = null) }
            WorkshopSetupAction.OnBusinessNameBlur ->
                if (_state.value.businessName.isNotBlank()) validateBusinessName()
            WorkshopSetupAction.OnWhatsAppNumberBlur ->
                if (_state.value.whatsappNumber.isNotBlank()) validateWhatsAppNumber()
            WorkshopSetupAction.OnContinueClick -> onContinue()
            WorkshopSetupAction.OnSkipClick -> onSkip()
            is WorkshopSetupAction.OnLogoPicked -> onLogoPicked(action.bytes)
            WorkshopSetupAction.OnLogoRetry -> onLogoRetry()
        }
    }

    private fun onLogoPicked(bytes: ByteArray) {
        when (val validation = logoValidator.validate(bytes)) {
            is Result.Error -> {
                viewModelScope.launch {
                    _events.send(WorkshopSetupEvent.ShowSnackbar(validation.error.toUiText()))
                }
                return
            }
            is Result.Success -> Unit
        }
        logoUploadJob?.cancel()
        _state.update { it.copy(logo = LogoUploadState.Uploading(bytes)) }
        logoUploadJob = viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(logo = LogoUploadState.Failed(bytes)) }
                return@launch
            }
            when (val result = userRepository.uploadUserLogo(userId, bytes)) {
                is Result.Success -> _state.update {
                    it.copy(logo = LogoUploadState.Uploaded(result.data.first, result.data.second))
                }
                is Result.Error -> {
                    _state.update { it.copy(logo = LogoUploadState.Failed(bytes)) }
                    _events.send(WorkshopSetupEvent.ShowSnackbar(BrandLogoError.Network(result.error).toUiText()))
                }
            }
        }
    }

    private fun onLogoRetry() {
        val failed = _state.value.logo as? LogoUploadState.Failed ?: return
        onLogoPicked(failed.previewBytes)
    }

    private fun onSkip() {
        val current = _state.value.logo
        logoUploadJob?.cancel()
        viewModelScope.launch {
            // Await the Storage delete BEFORE navigating. NavigateToHome clears this
            // onboarding ViewModel, cancelling viewModelScope — if the delete runs in
            // a separate launch it gets cancelled mid-flight and the logo object is
            // left orphaned in Storage. Joining keeps the cleanup tied to the screen
            // exit; the delete returns Result.Success even on a missing object (see
            // FirebaseUserRepository.deleteUserLogo) so a no-op path is still cheap.
            when (current) {
                is LogoUploadState.Uploaded -> userRepository.deleteUserLogo(current.path)
                is LogoUploadState.Uploading,
                is LogoUploadState.Failed -> {
                    val uid = authRepository.getCurrentUser()?.id
                    if (uid != null) {
                        userRepository.deleteUserLogo("users/$uid/branding/logo.jpg")
                    }
                }
                LogoUploadState.Empty -> Unit
            }
            onboardingPreferences.setWorkshopSetupCompleted()
            _events.send(WorkshopSetupEvent.NavigateToHome)
        }
    }

    private fun validateBusinessName(): Boolean {
        val name = _state.value.businessName.trim()
        if (name.length < MIN_BUSINESS_NAME_LEN) {
            _state.update { it.copy(businessNameError = Res.string.error_business_name_too_short) }
            return false
        }
        return true
    }

    private fun validateWhatsAppNumber(): Boolean {
        val raw = _state.value.whatsappNumber.trim()
        if (raw.isBlank()) return true
        val withCountry = applyImpliedNigerianCountryCode(raw)
        return if (validateNigerianMobileE164(withCountry)) {
            true
        } else {
            _state.update { it.copy(whatsappError = Res.string.error_whatsapp_invalid) }
            false
        }
    }

    @Suppress("LongMethod")
    private fun onContinue() {
        if (!validateBusinessName() || !validateWhatsAppNumber()) return

        viewModelScope.launch {
            // If a logo upload is still in flight, await it so we can persist the URL.
            val pending = logoUploadJob
            if (pending != null && pending.isActive) {
                _state.update { it.copy(isAwaitingLogo = true) }
                pending.join()
                _state.update { it.copy(isAwaitingLogo = false) }
            }

            val s = _state.value
            _state.update { it.copy(isLoading = true) }
            try {
                val user = authRepository.getCurrentUser() ?: run {
                    _events.send(
                        WorkshopSetupEvent.ShowError(
                            UiText.StringResourceText(Res.string.error_session_expired)
                        )
                    )
                    _events.send(WorkshopSetupEvent.NavigateToLogin)
                    return@launch
                }
                val whatsappE164 = s.whatsappNumber.trim().takeIf { it.isNotBlank() }
                    ?.let { "+" + normaliseNigerianPhone(applyImpliedNigerianCountryCode(it)) }

                val profileResult = userRepository.createUserProfile(
                    userId = user.id,
                    businessName = s.businessName.trim().ifBlank { null },
                    whatsappNumber = whatsappE164,
                )
                if (profileResult is Result.Error) {
                    _events.send(WorkshopSetupEvent.ShowError(UiText.StringResourceText(Res.string.error_unknown)))
                    return@launch
                }

                val logo = s.logo
                if (logo is LogoUploadState.Uploaded) {
                    val r = userRepository.updateBrandLogo(user.id, logo.url, logo.path)
                    if (r is Result.Success) {
                        _events.send(
                            WorkshopSetupEvent.ShowSnackbar(
                                UiText.StringResourceText(Res.string.workshop_logo_uploaded)
                            )
                        )
                    }
                    // A failure here doesn't block onboarding — the user has a profile.
                    // We just don't surface the success snackbar; they'll set the logo from Edit Profile.
                }

                onboardingPreferences.setWorkshopSetupCompleted()
                _events.send(WorkshopSetupEvent.NavigateToHome)
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private companion object {
        const val MAX_WHATSAPP_DIGITS = 13
        const val MIN_BUSINESS_NAME_LEN = 2

        fun capWhatsAppDigits(raw: String): String = buildString {
            var digits = 0
            raw.forEach { c ->
                val isDigit = c.isDigit()
                val isAcceptedFormatting = c in "+- ()"
                val keep = when {
                    isDigit && digits < MAX_WHATSAPP_DIGITS -> true.also { digits++ }
                    isAcceptedFormatting -> true
                    else -> false
                }
                if (keep) append(c)
            }
        }
    }
}
