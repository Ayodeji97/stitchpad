package com.danzucker.stitchpad.feature.style.presentation.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.repository.StyleRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.style.domain.StyleError
import com.danzucker.stitchpad.feature.style.presentation.toStyleUiText
import com.danzucker.stitchpad.feature.style.presentation.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val MAX_PHOTO_SIZE_BYTES: Int = 5 * 1024 * 1024

class StyleFormViewModel(
    savedStateHandle: SavedStateHandle,
    private val styleRepository: StyleRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val customerId: String = checkNotNull(savedStateHandle["customerId"])
    private val styleId: String? = savedStateHandle["styleId"]

    private var hasLoadedInitialData = false
    private val _state = MutableStateFlow(StyleFormState(isEditMode = styleId != null))

    private val _events = Channel<StyleFormEvent>()
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                hasLoadedInitialData = true
                if (styleId != null) loadStyle(styleId)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = StyleFormState(isEditMode = styleId != null)
        )

    fun onAction(action: StyleFormAction) {
        when (action) {
            is StyleFormAction.OnDescriptionChange -> {
                _state.update { it.copy(description = action.description) }
            }
            is StyleFormAction.OnPhotoPicked -> onPhotoPicked(action.bytes)
            StyleFormAction.OnSaveClick -> save()
            StyleFormAction.OnNavigateBack -> {
                viewModelScope.launch { _events.send(StyleFormEvent.NavigateBack) }
            }
            StyleFormAction.OnErrorDismiss -> {
                _state.update { it.copy(errorMessage = null) }
            }
        }
    }

    private fun onPhotoPicked(bytes: ByteArray) {
        if (bytes.size > MAX_PHOTO_SIZE_BYTES) {
            _state.update { it.copy(errorMessage = StyleError.PHOTO_TOO_LARGE.toUiText()) }
            return
        }
        _state.update { it.copy(selectedPhotoBytes = bytes) }
    }

    private fun loadStyle(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val userId = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            when (val result = styleRepository.observeStyles(userId, customerId).first()) {
                is Result.Success -> {
                    val style = result.data.find { it.id == id }
                    if (style != null) {
                        _state.update {
                            it.copy(
                                description = style.description,
                                existingStyle = style,
                                isLoading = false
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = StyleError.NOT_FOUND.toUiText()
                            )
                        }
                    }
                }
                is Result.Error -> _state.update {
                    it.copy(isLoading = false, errorMessage = result.error.toStyleUiText())
                }
            }
        }
    }

    private fun save() {
        val s = _state.value
        val trimmedDescription = s.description.trim()
        if (trimmedDescription.isBlank()) return
        if (!s.isEditMode && s.selectedPhotoBytes == null) return

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val userId = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(isSaving = false) }
                return@launch
            }
            val result = if (s.isEditMode && s.existingStyle != null) {
                styleRepository.updateStyle(
                    userId = userId,
                    customerId = customerId,
                    style = s.existingStyle.copy(description = trimmedDescription),
                    newPhotoBytes = s.selectedPhotoBytes
                )
            } else {
                styleRepository.createStyle(
                    userId = userId,
                    customerId = customerId,
                    description = trimmedDescription,
                    photoBytes = s.selectedPhotoBytes ?: ByteArray(0)
                )
            }
            _state.update { it.copy(isSaving = false) }
            when (result) {
                is Result.Success -> _events.send(StyleFormEvent.NavigateBack)
                is Result.Error -> _state.update {
                    it.copy(errorMessage = result.error.toStyleUiText())
                }
            }
        }
    }
}
