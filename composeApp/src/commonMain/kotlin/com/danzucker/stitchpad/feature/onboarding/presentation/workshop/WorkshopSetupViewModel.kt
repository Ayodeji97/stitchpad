package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.core.presentation.UiText
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
import stitchpad.composeapp.generated.resources.error_phone_invalid
import stitchpad.composeapp.generated.resources.error_phone_too_long
import stitchpad.composeapp.generated.resources.error_phone_too_short
import stitchpad.composeapp.generated.resources.error_unknown

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
            is WorkshopSetupAction.OnPhoneChange -> {
                val filtered = action.phone.filter { it.isDigit() || it in "+- ()" }.take(20)
                _state.update { it.copy(phone = filtered, phoneError = null) }
            }
            WorkshopSetupAction.OnBusinessNameBlur -> {
                if (_state.value.businessName.isNotBlank()) validateBusinessName()
            }
            WorkshopSetupAction.OnPhoneBlur -> {
                if (_state.value.phone.isNotBlank()) validatePhone()
            }
            WorkshopSetupAction.OnContinueClick -> onContinue()
            WorkshopSetupAction.OnSkipClick -> {
                viewModelScope.launch {
                    onboardingPreferences.setWorkshopSetupCompleted()
                    _events.send(WorkshopSetupEvent.NavigateToHome)
                }
            }
        }
    }

    private fun validateBusinessName(): Boolean {
        val name = _state.value.businessName
        if (name.isNotBlank() && name.trim().length < 2) {
            _state.update { it.copy(businessNameError = Res.string.error_business_name_too_short) }
            return false
        }
        return true
    }

    private fun validatePhone(): Boolean {
        val phone = _state.value.phone
        if (phone.isBlank()) return true
        val digitsOnly = phone.filter { it.isDigit() }
        val hasInvalidChars = phone.any { !it.isDigit() && it !in "+- ()" }
        return when {
            hasInvalidChars -> {
                _state.update { it.copy(phoneError = Res.string.error_phone_invalid) }
                false
            }
            digitsOnly.length < 7 -> {
                _state.update { it.copy(phoneError = Res.string.error_phone_too_short) }
                false
            }
            digitsOnly.length > 15 -> {
                _state.update { it.copy(phoneError = Res.string.error_phone_too_long) }
                false
            }
            else -> true
        }
    }

    private fun onContinue() {
        val nameValid = validateBusinessName()
        val phoneValid = validatePhone()
        if (!nameValid || !phoneValid) return

        val currentState = _state.value
        val hasData = currentState.businessName.isNotBlank() || currentState.phone.isNotBlank()

        if (!hasData) {
            viewModelScope.launch {
                onboardingPreferences.setWorkshopSetupCompleted()
                _events.send(WorkshopSetupEvent.NavigateToHome)
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val user = authRepository.getCurrentUser()
                if (user == null) {
                    _events.send(WorkshopSetupEvent.NavigateToHome)
                    return@launch
                }

                val result = userRepository.createUserProfile(
                    userId = user.id,
                    businessName = currentState.businessName.ifBlank { null },
                    phone = currentState.phone.ifBlank { null }
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
}
