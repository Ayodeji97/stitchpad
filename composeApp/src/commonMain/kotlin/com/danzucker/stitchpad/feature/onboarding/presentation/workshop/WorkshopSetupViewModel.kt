package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WorkshopSetupViewModel(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository
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
                _state.update { it.copy(phone = action.phone, phoneError = null) }
            }
            WorkshopSetupAction.OnContinueClick -> onContinue()
            WorkshopSetupAction.OnSkipClick -> {
                viewModelScope.launch {
                    _events.send(WorkshopSetupEvent.NavigateToHome)
                }
            }
        }
    }

    private fun validate(): Boolean {
        val currentState = _state.value
        var isValid = true

        if (currentState.businessName.isNotBlank() && currentState.businessName.trim().length < 2) {
            _state.update { it.copy(businessNameError = "Business name must be at least 2 characters") }
            isValid = false
        }

        if (currentState.phone.isNotBlank()) {
            val digitsOnly = currentState.phone.filter { it.isDigit() }
            val hasInvalidChars = currentState.phone.any { !it.isDigit() && it !in "+- ()" }
            when {
                hasInvalidChars -> {
                    _state.update { it.copy(phoneError = "Enter a valid phone number") }
                    isValid = false
                }
                digitsOnly.length < 7 -> {
                    _state.update { it.copy(phoneError = "Phone number is too short") }
                    isValid = false
                }
                digitsOnly.length > 15 -> {
                    _state.update { it.copy(phoneError = "Phone number is too long") }
                    isValid = false
                }
            }
        }

        return isValid
    }

    private fun onContinue() {
        if (!validate()) return

        val currentState = _state.value
        val hasData = currentState.businessName.isNotBlank() || currentState.phone.isNotBlank()

        if (!hasData) {
            viewModelScope.launch {
                _events.send(WorkshopSetupEvent.NavigateToHome)
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            val user = authRepository.getCurrentUser()
            if (user == null) {
                _state.update { it.copy(isLoading = false) }
                _events.send(WorkshopSetupEvent.NavigateToHome)
                return@launch
            }

            val result = userRepository.createUserProfile(
                userId = user.id,
                businessName = currentState.businessName.ifBlank { null },
                phone = currentState.phone.ifBlank { null }
            )
            _state.update { it.copy(isLoading = false) }

            when (result) {
                is Result.Success -> _events.send(WorkshopSetupEvent.NavigateToHome)
                is Result.Error -> _events.send(
                    WorkshopSetupEvent.ShowError(
                        UiText.DynamicString("Something went wrong. Please try again.")
                    )
                )
            }
        }
    }
}
