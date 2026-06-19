package com.danzucker.stitchpad.feature.notification.presentation.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.repository.NotificationRepository
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_unknown

class NotificationsInboxViewModel(
    private val notificationRepository: NotificationRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private var hasLoaded = false
    private val _state = MutableStateFlow(NotificationsInboxState())
    private val _events = Channel<NotificationsInboxEvent>()
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasLoaded) {
                hasLoaded = true
                observe()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), NotificationsInboxState())

    fun onAction(action: NotificationsInboxAction) {
        when (action) {
            NotificationsInboxAction.OnBackClick ->
                viewModelScope.launch { _events.send(NotificationsInboxEvent.NavigateBack) }
            is NotificationsInboxAction.OnNotificationClick -> onNotificationClick(action)
            NotificationsInboxAction.OnMarkAllReadClick -> markAllRead()
        }
    }

    private fun observe() {
        viewModelScope.launch {
            val uid = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            notificationRepository.observeNotifications(uid).collect { result ->
                when (result) {
                    is Result.Success -> _state.update {
                        // Also clear any prior errorMessage so a recovered load
                        // (after retryWhen self-heals) clears the error state.
                        it.copy(notifications = result.data, isLoading = false, errorMessage = null)
                    }
                    is Result.Error -> _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = UiText.StringResourceText(Res.string.error_unknown),
                        )
                    }
                }
            }
        }
    }

    private fun onNotificationClick(action: NotificationsInboxAction.OnNotificationClick) {
        viewModelScope.launch {
            val uid = authRepository.getCurrentUser()?.id ?: return@launch
            notificationRepository.markAsRead(uid, action.notification.id)
            // Gift notifications carry no order — tapping just marks them read.
            val orderId = action.notification.orderId
            if (orderId.isNotBlank()) {
                _events.send(NotificationsInboxEvent.NavigateToOrderDetail(orderId))
            }
        }
    }

    private fun markAllRead() {
        viewModelScope.launch {
            val uid = authRepository.getCurrentUser()?.id ?: return@launch
            val unreadIds = _state.value.notifications.filter { !it.isRead }.map { it.id }
            notificationRepository.markAllRead(uid, unreadIds)
        }
    }
}
