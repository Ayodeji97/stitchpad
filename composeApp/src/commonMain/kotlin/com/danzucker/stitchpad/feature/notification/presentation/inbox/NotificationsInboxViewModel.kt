package com.danzucker.stitchpad.feature.notification.presentation.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.NotificationType
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

    /**
     * Unread ids from the UNFILTERED snapshot, including rows hidden from display
     * (see the UNKNOWN filter in [observe]). Mark-all-read uses this so the
     * server-side unread badge can always be cleared.
     */
    private var allUnreadIds: List<String> = emptyList()
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
                    is Result.Success -> {
                        // Drop UNKNOWN rows. NotificationMapper maps any type it does not
                        // recognise to UNKNOWN, and NotificationRow renders that as a blank
                        // line (no customerName, no garmentSummary). Filtering here is what
                        // makes the inbox forward-compatible: the server can introduce a new
                        // notification type without waiting for every client to update,
                        // because older builds hide it rather than showing an empty row.
                        // Rules forbid client delete, so a bad row would otherwise be stuck
                        // in a user's inbox permanently.
                        val visible = result.data.filter { it.type != NotificationType.UNKNOWN }
                        // Mark-all-read must cover the HIDDEN rows too. The dashboard bell
                        // counts unread docs server-side (observeUnreadCount) and does not
                        // know about this filter, so marking only the visible ones would
                        // leave the badge showing a count the tailor can see no way to
                        // clear — they open an empty inbox and the badge never goes away.
                        allUnreadIds = result.data.filter { !it.isRead }.map { it.id }
                        // Also clear any prior errorMessage so a recovered load
                        // (after retryWhen self-heals) clears the error state.
                        _state.update {
                            it.copy(notifications = visible, isLoading = false, errorMessage = null)
                        }
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
            notificationRepository.markAllRead(uid, allUnreadIds)
        }
    }
}
