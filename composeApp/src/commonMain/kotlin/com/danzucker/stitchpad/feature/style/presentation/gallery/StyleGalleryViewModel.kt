package com.danzucker.stitchpad.feature.style.presentation.gallery

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.CustomerSlotState
import com.danzucker.stitchpad.core.domain.model.StyleLocation
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

    private val location: StyleLocation =
        customerId?.let(StyleLocation::CustomerCloset) ?: StyleLocation.Inspiration

    private var hasLoadedInitialData = false
    private val isInspirationGallery = location is StyleLocation.Inspiration
    private val _state = MutableStateFlow(
        StyleGalleryState(isInspirationGallery = isInspirationGallery)
    )

    private val _events = Channel<StyleGalleryEvent>()
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                hasLoadedInitialData = true
                observeStyles()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = StyleGalleryState(isInspirationGallery = isInspirationGallery)
        )

    @Suppress("CyclomaticComplexMethod")
    fun onAction(action: StyleGalleryAction) {
        when (action) {
            StyleGalleryAction.OnAddClick -> {
                viewModelScope.launch { _events.send(StyleGalleryEvent.NavigateToAddStyle(customerId)) }
            }
            is StyleGalleryAction.OnStyleClick -> {
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
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            // Other ACTIVE customers only — you can't add to a locked customer, and
            // the source customer isn't a valid target.
            val inspirationTargets = listOfNotNull(
                TransferTarget.Inspiration.takeIf { location is StyleLocation.CustomerCloset }
            )
            val targets = when (val result = customerRepository.observeCustomers(userId).first()) {
                is Result.Success ->
                    inspirationTargets + result.data
                        .filter { it.id != customerId && it.slotState == CustomerSlotState.ACTIVE }
                        .map { TransferTarget.Customer(customerId = it.id, name = it.name) }
                is Result.Error -> inspirationTargets
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
        val target = transfer.targets.firstOrNull { it.id == targetCustomerId } ?: return
        _state.update { it.copy(transfer = null) }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            val result = when (transfer.mode) {
                StyleTransferMode.COPY ->
                    styleRepository.copyStyle(
                        userId,
                        from = location,
                        transfer.style,
                        to = target.location,
                    )
                StyleTransferMode.MOVE ->
                    styleRepository.moveStyle(
                        userId,
                        from = location,
                        transfer.style,
                        to = target.location,
                    )
            }
            when (result) {
                is Result.Success ->
                    _events.send(
                        StyleGalleryEvent.StyleTransferred(
                            mode = transfer.mode,
                            target = target,
                        )
                    )
                is Result.Error ->
                    _state.update { it.copy(errorMessage = result.error.toStyleUiText()) }
            }
        }
    }

    private fun observeStyles() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            styleRepository.observeStyles(userId, location).collect { result ->
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
        val style = _state.value.styleToDelete ?: return
        _state.update { it.copy(showDeleteDialog = false, styleToDelete = null) }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            val result = styleRepository.deleteStyle(userId, location, style)
            if (result is Result.Error) {
                _state.update { it.copy(errorMessage = result.error.toStyleUiText()) }
            }
        }
    }
}
