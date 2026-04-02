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
                _state.update { it.copy(businessName = action.name) }
            }
            is WorkshopSetupAction.OnPhoneChange -> {
                _state.update { it.copy(phone = action.phone) }
            }
            WorkshopSetupAction.OnContinueClick -> onContinue()
            WorkshopSetupAction.OnSkipClick -> {
                viewModelScope.launch {
                    _events.send(WorkshopSetupEvent.NavigateToHome)
                }
            }
        }
    }

    private fun onContinue() {
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
