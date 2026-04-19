package com.danzucker.stitchpad.feature.style.presentation.gallery

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.repository.StyleRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.style.presentation.toStyleUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StyleGalleryViewModel(
    savedStateHandle: SavedStateHandle,
    private val styleRepository: StyleRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val customerId: String = checkNotNull(savedStateHandle["customerId"])

    private var hasLoadedInitialData = false
    private val _state = MutableStateFlow(StyleGalleryState())

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
            initialValue = StyleGalleryState()
        )

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
            is StyleGalleryAction.OnDeleteClick -> {
                _state.update { it.copy(showDeleteDialog = true, styleToDelete = action.style) }
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

    private fun observeStyles() {
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
