package com.danzucker.stitchpad.feature.style.presentation.gallery

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.CustomerSlotState
import com.danzucker.stitchpad.core.domain.model.StyleLocation
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.StyleRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.style.domain.StyleCollectionLimits
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
    private val authRepository: AuthRepository,
    private val entitlements: EntitlementsProvider,
) : ViewModel() {

    private val customerId: String? = savedStateHandle["customerId"]
    private val folderId: String? = savedStateHandle["folderId"]

    private val location: StyleLocation =
        customerId?.let { StyleLocation.CustomerCloset(it, folderId) } ?: StyleLocation.Inspiration(folderId)

    // Cached resolved cap — null until first awaitHydrated() call completes.
    private var resolvedImageCap: Int? = null

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
                viewModelScope.launch {
                    val cap = resolveImageCap()
                    if (_state.value.styles.size >= cap) {
                        _events.send(StyleGalleryEvent.CapReached(cap))
                    } else {
                        _events.send(StyleGalleryEvent.NavigateToAddStyle(customerId, folderId))
                    }
                }
            }
            is StyleGalleryAction.OnStyleClick -> {
                viewModelScope.launch {
                    _events.send(StyleGalleryEvent.NavigateToEditStyle(customerId, folderId, action.style.id))
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

    /**
     * Resolves the image cap for the current location, awaiting entitlements hydration
     * on the first call and caching thereafter.
     */
    private suspend fun resolveImageCap(): Int {
        resolvedImageCap?.let { return it }
        val limits = if (customerId == null) {
            StyleCollectionLimits.forInspiration(entitlements.awaitHydrated().tier)
        } else {
            StyleCollectionLimits.forCustomer(entitlements.awaitHydrated().tier)
        }
        val cap = if (!limits.foldersEnabled) limits.flatCap else limits.maxImagesPerFolder
        resolvedImageCap = cap
        return cap
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

    // The destination is the same tailor's own data, so the cap is resolved from the
    // current tier at the destination's level. Transfers land in the destination's flat
    // default folder, so the cap is flatCap (Free) or maxImagesPerFolder (paid default).
    private fun destinationCap(target: TransferTarget): Int {
        val targetLimits = when (target) {
            is TransferTarget.Inspiration -> StyleCollectionLimits.forInspiration(entitlements.current().tier)
            is TransferTarget.Customer -> StyleCollectionLimits.forCustomer(entitlements.current().tier)
        }
        return if (!targetLimits.foldersEnabled) targetLimits.flatCap else targetLimits.maxImagesPerFolder
    }

    @Suppress("ReturnCount")
    private fun transferTo(targetCustomerId: String) {
        val transfer = _state.value.transfer ?: return
        val target = transfer.targets.firstOrNull { it.id == targetCustomerId } ?: return
        _state.update { it.copy(transfer = null) }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            // Block the transfer if the destination's default folder is already full —
            // keeps copy/move from silently overflowing a folder past its cap.
            val cap = destinationCap(target)
            val destCount = when (val r = styleRepository.observeStyles(userId, target.location).first()) {
                is Result.Success -> r.data.size
                is Result.Error -> 0
            }
            if (destCount >= cap) {
                _events.send(StyleGalleryEvent.CapReached(cap))
                return@launch
            }
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
