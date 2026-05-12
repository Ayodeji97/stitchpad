package com.danzucker.stitchpad.feature.auth.presentation.deleteaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.auth.domain.AuthError
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeleteAccountViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DeleteAccountState())
    val state = _state.asStateFlow()

    private val _events = Channel<DeleteAccountEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: DeleteAccountAction) {
        when (action) {
            DeleteAccountAction.OnConfirm -> deleteAccount()
        }
    }

    private fun deleteAccount() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                when (val result = authRepository.deleteAccount()) {
                    is Result.Success -> _events.send(DeleteAccountEvent.AccountDeleted)
                    is Result.Error -> {
                        val event = if (result.error == AuthError.REQUIRES_RECENT_LOGIN) {
                            DeleteAccountEvent.ReauthRequired
                        } else {
                            DeleteAccountEvent.ShowGenericError
                        }
                        _events.send(event)
                    }
                }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }
}

data class DeleteAccountState(val isLoading: Boolean = false)

sealed interface DeleteAccountAction {
    data object OnConfirm : DeleteAccountAction
}

sealed interface DeleteAccountEvent {
    data object AccountDeleted : DeleteAccountEvent
    data object ReauthRequired : DeleteAccountEvent
    data object ShowGenericError : DeleteAccountEvent
}
