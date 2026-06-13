package com.danzucker.stitchpad.feature.style.presentation.gallery

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.CustomerSlotState
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.StyleRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.style.presentation.toStyleUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StyleGalleryViewModel(
    savedStateHandle: SavedStateHandle,
    private val styleRepository: StyleRepository,
    private val customerRepository: CustomerRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val customerId: String? = savedStateHandle["customerId"]

    private var hasLoadedInitialData = false
    private val _state = MutableStateFlow(StyleGalleryState())

    private val _events = Channel<StyleGalleryEvent>()
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                hasLoadedInitialData = true
                if (customerId == null) {
                    _state.update { it.copy(isLoading = false) }
                    _events.send(StyleGalleryEvent.NavigateBack)
                    return@onStart
                }
                observeStyles()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = StyleGalleryState()
        )

    @Suppress("CyclomaticComplexMethod")
    fun onAction(action: StyleGalleryAction) {
        when (action) {
            StyleGalleryAction.OnAddClick -> {
                val customerId = customerId ?: return
                viewModelScope.launch { _events.send(StyleGalleryEvent.NavigateToAddStyle(customerId)) }
            }
            is StyleGalleryAction.OnStyleClick -> {
                val customerId = customerId ?: return
                viewModelScope.launch {
                    _events.send(StyleGalleryEvent.NavigateToEditStyle(customerId, action.style.id))
                }
            }
            is StyleGalleryAction.OnStyleLongPress -> {
                _state.update { it.copy(actionSheetStyle = action.style) }
            }
            StyleGalleryAction.OnDismissActionSheet -> {
                _state.update { it.copy(actionSheetStyle = null) }
            }
            StyleGalleryAction.OnCopyClick -> openTransfer(StyleTransferMode.COPY)
            StyleGalleryAction.OnMoveClick -> openTransfer(StyleTransferMode.MOVE)
            is StyleGalleryAction.OnTargetCustomerSelected -> transferTo(action.customerId)
            StyleGalleryAction.OnDismissTransfer -> {
                _state.update { it.copy(transfer = null) }
            }
            is StyleGalleryAction.OnDeleteClick -> {
                _state.update {
                    it.copy(actionSheetStyle = null, showDeleteDialog = true, styleToDelete = action.style)
                }
            }
            StyleGalleryAction.OnConfirmDelete -> deleteStyle()
            StyleGalleryAction.OnDismissDeleteDialog -> {
                _state.update { it.copy(showDeleteDialog = false, styleToDelete = null) }
            }
            StyleGalleryAction.OnNavigateBack -> {
                viewModelScope.launch { _events.send(StyleGalleryEvent.NavigateBack) }
            }
            StyleGalleryAction.OnErrorDismiss -> {
                _state.update { it.copy(errorMessage = null) }
            }
        }
    }

    private fun openTransfer(mode: StyleTransferMode) {
        val style = _state.value.actionSheetStyle ?: return
        val currentCustomerId = customerId ?: return
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            // Other ACTIVE customers only — you can't add to a locked customer, and
            // the source customer isn't a valid target.
            val targets = when (val result = customerRepository.observeCustomers(userId).first()) {
                is Result.Success ->
                    result.data
                        .filter { it.id != currentCustomerId && it.slotState == CustomerSlotState.ACTIVE }
                        .map { TransferTarget(id = it.id, name = it.name) }
                is Result.Error -> emptyList()
            }
            _state.update {
                it.copy(
                    actionSheetStyle = null,
                    transfer = StyleTransfer(style = style, mode = mode, targets = targets)
                )
            }
        }
    }

    @Suppress("ReturnCount")
    private fun transferTo(targetCustomerId: String) {
        val transfer = _state.value.transfer ?: return
        val fromCustomerId = customerId ?: return
        val targetName = transfer.targets.firstOrNull { it.id == targetCustomerId }?.name ?: return
        _state.update { it.copy(transfer = null) }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            val result = when (transfer.mode) {
                StyleTransferMode.COPY ->
                    styleRepository.copyStyle(userId, fromCustomerId, transfer.style, targetCustomerId)
                StyleTransferMode.MOVE ->
                    styleRepository.moveStyle(userId, fromCustomerId, transfer.style, targetCustomerId)
            }
            when (result) {
                is Result.Success ->
                    _events.send(StyleGalleryEvent.StyleTransferred(transfer.mode, targetName))
                is Result.Error ->
                    _state.update { it.copy(errorMessage = result.error.toStyleUiText()) }
            }
        }
    }

    private fun observeStyles() {
        val customerId = customerId ?: return
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            styleRepository.observeStyles(userId, customerId).collect { result ->
                when (result) {
                    is Result.Success -> _state.update {
                        it.copy(styles = result.data, isLoading = false)
                    }
                    is Result.Error -> _state.update {
                        it.copy(isLoading = false, errorMessage = result.error.toStyleUiText())
                    }
                }
            }
        }
    }

    private fun deleteStyle() {
        val customerId = customerId ?: return
        val style = _state.value.styleToDelete ?: return
        _state.update { it.copy(showDeleteDialog = false, styleToDelete = null) }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            val result = styleRepository.deleteStyle(userId, customerId, style)
            if (result is Result.Error) {
                _state.update { it.copy(errorMessage = result.error.toStyleUiText()) }
            }
        }
    }
}
