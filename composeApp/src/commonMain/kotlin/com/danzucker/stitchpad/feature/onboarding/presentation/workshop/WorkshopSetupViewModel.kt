package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.core.sharing.normaliseNigerianPhone
import com.danzucker.stitchpad.core.sharing.validateNigerianMobileE164
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferencesStore
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

class WorkshopSetupViewModel(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val onboardingPreferences: OnboardingPreferencesStore
) : ViewModel() {

    private val _state = MutableStateFlow(WorkshopSetupState())
    val state = _state.asStateFlow()

    private val _events = Channel<WorkshopSetupEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: WorkshopSetupAction) {
        when (action) {
            is WorkshopSetupAction.OnBusinessNameChange -> {
                _state.update { it.copy(businessName = action.name, businessNameError = null) }
            }
            is WorkshopSetupAction.OnWhatsAppNumberChange -> {
                val capped = capWhatsAppDigits(action.raw)
                _state.update { it.copy(whatsappNumber = capped, whatsappError = null) }
            }
            WorkshopSetupAction.OnBusinessNameBlur -> {
                if (_state.value.businessName.isNotBlank()) validateBusinessName()
            }
            WorkshopSetupAction.OnWhatsAppNumberBlur -> {
                if (_state.value.whatsappNumber.isNotBlank()) validateWhatsAppNumber()
            }
            WorkshopSetupAction.OnContinueClick -> onContinue()
            WorkshopSetupAction.OnSkipClick -> {
                viewModelScope.launch {
                    onboardingPreferences.setWorkshopSetupCompleted()
                    _events.send(WorkshopSetupEvent.NavigateToHome)
                }
            }
            WorkshopSetupAction.OnLogoUploadClick -> {
                viewModelScope.launch { _events.send(WorkshopSetupEvent.ShowComingSoon) }
            }
        }
    }

    private fun validateBusinessName(): Boolean {
        val name = _state.value.businessName.trim()
        if (name.length < 2) {
            _state.update { it.copy(businessNameError = Res.string.error_business_name_too_short) }
            return false
        }
        return true
    }

    private fun validateWhatsAppNumber(): Boolean {
        val raw = _state.value.whatsappNumber
        return if (validateNigerianMobileE164(raw)) {
            true
        } else {
            _state.update { it.copy(whatsappError = Res.string.error_whatsapp_invalid) }
            false
        }
    }

    private fun onContinue() {
        val nameValid = validateBusinessName()
        val waValid = validateWhatsAppNumber()
        if (!nameValid || !waValid) return

        val state = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val user = authRepository.getCurrentUser()
                if (user == null) {
                    _events.send(
                        WorkshopSetupEvent.ShowError(
                            UiText.StringResourceText(Res.string.error_session_expired)
                        )
                    )
                    _events.send(WorkshopSetupEvent.NavigateToLogin)
                    return@launch
                }
                val whatsappE164 = state.whatsappNumber.trim().takeIf { it.isNotBlank() }
                    ?.let { "+" + normaliseNigerianPhone(it) }
                val result = userRepository.createUserProfile(
                    userId = user.id,
                    businessName = state.businessName.trim().ifBlank { null },
                    whatsappNumber = whatsappE164,
                )
                when (result) {
                    is Result.Success -> {
                        onboardingPreferences.setWorkshopSetupCompleted()
                        _events.send(WorkshopSetupEvent.NavigateToHome)
                    }
                    is Result.Error -> _events.send(
                        WorkshopSetupEvent.ShowError(
                            UiText.StringResourceText(Res.string.error_unknown)
                        )
                    )
                }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private companion object {
        // Nigerian E.164 has 13 digits (country code 234 + 10-digit subscriber).
        // Cap on digit count, not raw length, so that formatted paste like
        // "+234 803 123 4567" is preserved (17 chars, 13 digits).
        const val MAX_WHATSAPP_DIGITS = 13

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
