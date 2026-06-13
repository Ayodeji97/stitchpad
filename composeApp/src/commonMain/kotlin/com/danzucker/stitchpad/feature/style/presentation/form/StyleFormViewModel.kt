package com.danzucker.stitchpad.feature.style.presentation.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.StyleImageRef
import com.danzucker.stitchpad.core.domain.model.StyleImageSource
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
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
    private val authRepository: AuthRepository,
    private val orderRepository: OrderRepository,
) : ViewModel() {

    private val customerId: String? = savedStateHandle["customerId"]
    private val styleId: String? = savedStateHandle["styleId"]
    private val linkToOrderId: String? = savedStateHandle["linkToOrderId"]

    // Multi-pick only when adding to a closet — not when editing one style and
    // not when attaching exactly one style to an order (the link flow).
    private val allowMultiPhoto: Boolean = styleId == null && linkToOrderId == null

    private var hasLoadedInitialData = false
    private val _state = MutableStateFlow(
        StyleFormState(isEditMode = styleId != null, allowMultiPhoto = allowMultiPhoto)
    )

    private val _events = Channel<StyleFormEvent>()
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                hasLoadedInitialData = true
                if (customerId == null) {
                    _state.update { it.copy(isLoading = false) }
                    _events.send(StyleFormEvent.NavigateBack)
                    return@onStart
                }
                if (styleId != null) loadStyle(styleId)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = StyleFormState(isEditMode = styleId != null, allowMultiPhoto = allowMultiPhoto)
        )

    fun onAction(action: StyleFormAction) {
        when (action) {
            is StyleFormAction.OnDescriptionChange -> {
                _state.update { it.copy(description = action.description) }
            }
            is StyleFormAction.OnPhotosPicked -> onPhotosPicked(action.photos)
            StyleFormAction.OnSaveClick -> save()
            StyleFormAction.OnNavigateBack -> {
                viewModelScope.launch { _events.send(StyleFormEvent.NavigateBack) }
            }
            StyleFormAction.OnErrorDismiss -> {
                _state.update { it.copy(errorMessage = null) }
            }
        }
    }

    private fun onPhotosPicked(photos: List<ByteArray>) {
        if (photos.isEmpty()) return
        // A fresh pick replaces the current selection. Reject the whole batch if
        // any image exceeds the cap so the user sees the failure before saving.
        if (photos.any { it.size > MAX_PHOTO_SIZE_BYTES }) {
            _state.update {
                it.copy(
                    errorMessage = StyleError.PHOTO_TOO_LARGE.toUiText(),
                    selectedPhotos = emptyList()
                )
            }
            return
        }
        _state.update { it.copy(selectedPhotos = photos, errorMessage = null) }
    }

    private fun loadStyle(id: String) {
        val customerId = customerId ?: return
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

    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    private fun save() {
        val customerId = customerId ?: return
        val s = _state.value
        val trimmedDescription = s.description.trim()
        val missingPhotoForCreate = !s.isEditMode && s.selectedPhotos.isEmpty()
        val missingStyleForEdit = s.isEditMode && s.existingStyle == null
        if (trimmedDescription.isBlank() || missingPhotoForCreate || missingStyleForEdit) return

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val userId = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(isSaving = false) }
                return@launch
            }
            if (s.isEditMode && s.existingStyle != null) {
                val updateResult = styleRepository.updateStyle(
                    userId = userId,
                    customerId = customerId,
                    style = s.existingStyle.copy(description = trimmedDescription),
                    newPhotoBytes = s.selectedPhotos.firstOrNull(),
                )
                _state.update { it.copy(isSaving = false) }
                when (updateResult) {
                    is Result.Success -> _events.send(StyleFormEvent.NavigateBack)
                    is Result.Error -> _state.update {
                        it.copy(errorMessage = updateResult.error.toStyleUiText())
                    }
                }
                return@launch
            }

            // Multi-photo closet add: batch-create and return — the order-link
            // path below is unreachable here (allowMultiPhoto requires no link).
            if (s.selectedPhotos.size > 1) {
                val batchResult = styleRepository.createStyles(
                    userId = userId,
                    customerId = customerId,
                    description = trimmedDescription,
                    photoBytesList = s.selectedPhotos,
                )
                _state.update { it.copy(isSaving = false) }
                when (batchResult) {
                    is Result.Success -> _events.send(StyleFormEvent.NavigateBack)
                    is Result.Error -> _state.update {
                        it.copy(errorMessage = batchResult.error.toStyleUiText())
                    }
                }
                return@launch
            }

            val createResult = styleRepository.createStyle(
                userId = userId,
                customerId = customerId,
                description = trimmedDescription,
                photoBytes = s.selectedPhotos.firstOrNull() ?: ByteArray(0),
            )
            if (createResult is Result.Error) {
                _state.update {
                    it.copy(isSaving = false, errorMessage = createResult.error.toStyleUiText())
                }
                return@launch
            }
            val newStyleId = (createResult as Result.Success).data

            // If opened from an order's "link" path, attach this style to items[0].
            // Mirror the measurement-link flow: failure here is silent — style is
            // already persisted; user can retry from the order detail picker.
            val linkOrderId = linkToOrderId
            if (linkOrderId != null) {
                when (val orderResult = orderRepository.getOrder(userId, linkOrderId)) {
                    is Result.Success -> {
                        val order = orderResult.data
                        val firstItem = order.items.firstOrNull()
                        if (firstItem != null) {
                            // PTSP-11 — APPEND a LIBRARY ref to the first item's styleImages
                            // list. Guard against duplicates and the 3-image cap.
                            val alreadyHas = firstItem.styleImages.any {
                                it.source == StyleImageSource.LIBRARY && it.styleId == newStyleId
                            }
                            val atCap = firstItem.styleImages.size >= 3
                            if (!alreadyHas && !atCap) {
                                val newRef = StyleImageRef(
                                    source = StyleImageSource.LIBRARY,
                                    styleId = newStyleId,
                                )
                                val updatedItem = firstItem.copy(
                                    styleImages = firstItem.styleImages + newRef,
                                )
                                val updatedItems = listOf(updatedItem) + order.items.drop(1)
                                orderRepository.updateOrder(userId, order.copy(items = updatedItems))
                            }
                        }
                    }
                    is Result.Error -> Unit
                }
            }

            _state.update { it.copy(isSaving = false) }
            _events.send(StyleFormEvent.NavigateBack)
        }
    }
}
