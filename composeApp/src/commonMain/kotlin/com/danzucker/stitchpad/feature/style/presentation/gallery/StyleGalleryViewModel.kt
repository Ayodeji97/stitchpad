package com.danzucker.stitchpad.feature.style.presentation.gallery

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.CustomerSlotState
import com.danzucker.stitchpad.core.domain.model.StyleLocation
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.StyleRepository
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.style.domain.StyleCollectionLimits
import com.danzucker.stitchpad.feature.style.domain.StyleLockPolicy
import com.danzucker.stitchpad.feature.style.domain.countStylesAcrossFolders
import com.danzucker.stitchpad.feature.style.domain.observeFoldersWithStyles
import com.danzucker.stitchpad.feature.style.presentation.cap.StyleCapKind
import com.danzucker.stitchpad.feature.style.presentation.cap.styleCapInfo
import com.danzucker.stitchpad.feature.style.presentation.toStyleUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.style_action_verify_failed

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

    // styleId -> the style's TRUE location (its folder). On Free the flat gallery shows
    // styles from many folders; edits/deletes must target each style's real folder, not
    // the gallery's root location. Rebuilt on every emission.
    private var entryLocations: Map<String, StyleLocation> = emptyMap()

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

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun onAction(action: StyleGalleryAction) {
        when (action) {
            StyleGalleryAction.OnAddClick -> {
                viewModelScope.launch {
                    val tier = entitlements.awaitHydrated().tier
                    val limits = if (customerId == null) {
                        StyleCollectionLimits.forInspiration(tier)
                    } else {
                        StyleCollectionLimits.forCustomer(tier)
                    }
                    val cap = if (!limits.foldersEnabled) limits.flatCap else limits.maxImagesPerFolder
                    if (_state.value.styles.size >= cap) {
                        _state.update { it.copy(capSheet = stylesCapInfo(tier)) }
                    } else {
                        _events.send(StyleGalleryEvent.NavigateToAddStyle(customerId, folderId))
                    }
                }
            }
            is StyleGalleryAction.OnStyleClick -> {
                viewModelScope.launch {
                    val locked = action.style.id in _state.value.lockedStyleIds
                    val loc = locationOf(action.style.id)
                    val targetFolderId = (loc as? StyleLocation.CustomerCloset)?.folderId
                        ?: (loc as? StyleLocation.Inspiration)?.folderId
                    _events.send(
                        StyleGalleryEvent.NavigateToEditStyle(
                            customerId = customerId,
                            folderId = targetFolderId,
                            styleId = action.style.id,
                            readOnly = locked,
                        )
                    )
                }
            }
            is StyleGalleryAction.OnStyleLongPress -> {
                if (action.style.id in _state.value.lockedStyleIds) {
                    viewModelScope.launch {
                        val tier = entitlements.awaitHydrated().tier
                        _state.update { it.copy(capSheet = stylesCapInfo(tier)) }
                    }
                } else {
                    _state.update { it.copy(actionSheetStyle = action.style) }
                }
            }
            StyleGalleryAction.OnDismissActionSheet -> {
                _state.update { it.copy(actionSheetStyle = null) }
            }
            StyleGalleryAction.OnCopyClick -> openTransfer(StyleTransferMode.COPY)
            StyleGalleryAction.OnMoveClick -> openTransfer(StyleTransferMode.MOVE)
            is StyleGalleryAction.OnTargetCustomerSelected -> onTargetSelected(action.customerId)
            is StyleGalleryAction.OnDestinationFolderSelected -> onDestinationFolderSelected(action.folderId)
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
            StyleGalleryAction.OnDismissCapSheet -> _state.update { it.copy(capSheet = null) }
            StyleGalleryAction.OnUpgradeFromCap -> {
                _state.update { it.copy(capSheet = null) }
                viewModelScope.launch { _events.send(StyleGalleryEvent.NavigateToUpgrade) }
            }
        }
    }

    private fun stylesCapInfo(tier: SubscriptionTier) =
        styleCapInfo(StyleCapKind.STYLES, tier, isInspiration = customerId == null)

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

    /**
     * Step 1 of the transfer flow: the user has selected the destination target.
     * - Free destination: transfer directly to the target&apos;s default folder (old behaviour).
     * - Paid destination (foldersEnabled): load the target&apos;s named folders and populate
     *   [StyleTransfer.destinationFolders] so the screen shows the folder-picker step.
     */
    @Suppress("ReturnCount", "LongMethod", "CyclomaticComplexMethod")
    private fun onTargetSelected(targetId: String) {
        val transfer = _state.value.transfer ?: return
        val target = transfer.targets.firstOrNull { it.id == targetId } ?: return
        viewModelScope.launch {
            val tier = entitlements.awaitHydrated().tier
            val limits = when (target) {
                is TransferTarget.Inspiration -> StyleCollectionLimits.forInspiration(tier)
                is TransferTarget.Customer -> StyleCollectionLimits.forCustomer(tier)
            }
            if (!limits.foldersEnabled) {
                // Free path: check the flat-folder cap (across ALL folders), then transfer directly.
                val userId = authRepository.getCurrentUser()?.id ?: return@launch
                // target.location is already a root location for Free targets.
                val destCount = styleRepository.countStylesAcrossFolders(userId, target.location)
                _state.update { it.copy(transfer = null) }
                if (destCount == null) {
                    // Count read failed — fail closed: abort and surface error.
                    _state.update {
                        it.copy(
                            errorMessage = UiText.StringResourceText(Res.string.style_action_verify_failed)
                        )
                    }
                    return@launch
                }
                if (destCount >= limits.flatCap) {
                    _state.update { it.copy(capSheet = stylesCapInfo(tier)) }
                    return@launch
                }
                performTransfer(transfer, target, destinationLocation = target.location, folderId = null)
            } else {
                // Paid path: build folder options and show the folder-picker.
                val userId = authRepository.getCurrentUser()?.id ?: return@launch
                val namedFolders = when (val r = styleRepository.observeFolders(userId, target.location).first()) {
                    is Result.Success -> r.data
                    is Result.Error -> emptyList()
                }
                val defaultCount = when (val r = styleRepository.observeStyles(userId, target.location).first()) {
                    is Result.Success -> r.data.size
                    is Result.Error -> 0
                }
                val defaultOption = TransferFolderOption(
                    folderId = null,
                    name = null,
                    count = defaultCount,
                    cap = limits.maxImagesPerFolder,
                )
                val namedOptions = namedFolders.map { folder ->
                    val folderLocation = when (target) {
                        is TransferTarget.Customer ->
                            StyleLocation.CustomerCloset(target.customerId, folder.id)
                        TransferTarget.Inspiration ->
                            StyleLocation.Inspiration(folder.id)
                    }
                    val count = when (val r = styleRepository.observeStyles(userId, folderLocation).first()) {
                        is Result.Success -> r.data.size
                        is Result.Error -> 0
                    }
                    TransferFolderOption(
                        folderId = folder.id,
                        name = folder.name,
                        count = count,
                        cap = limits.maxImagesPerFolder,
                    )
                }
                _state.update {
                    it.copy(
                        transfer = transfer.copy(
                            selectedTarget = target,
                            destinationFolders = listOf(defaultOption) + namedOptions,
                        )
                    )
                }
            }
        }
    }

    /**
     * Step 2 of the transfer flow (paid path only): the user has chosen a specific
     * destination folder. Validates the cap then executes copy/move.
     */
    @Suppress("ReturnCount")
    private fun onDestinationFolderSelected(folderId: String?) {
        val transfer = _state.value.transfer ?: return
        val target = transfer.selectedTarget ?: return
        val option = transfer.destinationFolders?.firstOrNull { it.folderId == folderId } ?: return
        if (option.isFull) {
            _state.update { it.copy(transfer = null) }
            viewModelScope.launch {
                val tier = entitlements.awaitHydrated().tier
                _state.update { it.copy(capSheet = stylesCapInfo(tier)) }
            }
            return
        }
        val destinationLocation = when (target) {
            is TransferTarget.Customer ->
                StyleLocation.CustomerCloset(target.customerId, folderId)
            TransferTarget.Inspiration ->
                StyleLocation.Inspiration(folderId)
        }
        _state.update { it.copy(transfer = null) }
        viewModelScope.launch {
            performTransfer(transfer, target, destinationLocation = destinationLocation, folderId = folderId)
        }
    }

    /**
     * Executes copy or move and emits the result event / error.
     *
     * FIX 5 + FIX 7(gallery): Re-reads the destination's live style count immediately
     * before committing the transfer. If the read fails, surface an error and abort.
     * If the count is at or above cap, emit CapReached and abort.
     */
    @Suppress("ReturnCount", "CyclomaticComplexMethod", "LongMethod")
    private suspend fun performTransfer(
        transfer: StyleTransfer,
        target: TransferTarget,
        destinationLocation: StyleLocation,
        folderId: String?,
    ) {
        val userId = authRepository.getCurrentUser()?.id ?: return
        // Resolve the cap for this destination.
        val tier = entitlements.awaitHydrated().tier
        val limits = when (target) {
            is TransferTarget.Inspiration -> StyleCollectionLimits.forInspiration(tier)
            is TransferTarget.Customer -> StyleCollectionLimits.forCustomer(tier)
        }
        val cap = if (!limits.foldersEnabled) limits.flatCap else limits.maxImagesPerFolder
        // Live re-read: any count read error → fail safe (abort, surface error).
        // On Free tier count the full flattened destination closet/library (flat cap
        // applies globally, not just to the root folder). On paid tiers count the
        // specific destination folder.
        val liveCount: Int
        if (!limits.foldersEnabled) {
            // Derive the destination root (folderId null) from the target.
            val destRoot = when (target) {
                is TransferTarget.Customer -> StyleLocation.CustomerCloset(target.customerId)
                TransferTarget.Inspiration -> StyleLocation.Inspiration()
            }
            val flatCount = styleRepository.countStylesAcrossFolders(userId, destRoot)
            if (flatCount == null) {
                // Count read failed — fail closed: abort and surface error.
                _state.update {
                    it.copy(
                        errorMessage = UiText.StringResourceText(Res.string.style_action_verify_failed)
                    )
                }
                return
            }
            liveCount = flatCount
        } else {
            when (val r = styleRepository.observeStyles(userId, destinationLocation).first()) {
                is Result.Success -> liveCount = r.data.size
                is Result.Error -> {
                    _state.update {
                        it.copy(
                            errorMessage = UiText.StringResourceText(Res.string.style_action_verify_failed)
                        )
                    }
                    return
                }
            }
        }
        if (liveCount >= cap) {
            _state.update { it.copy(capSheet = stylesCapInfo(tier)) }
            return
        }
        val sourceLocation = locationOf(transfer.style.id)
        val result = when (transfer.mode) {
            StyleTransferMode.COPY ->
                styleRepository.copyStyle(userId, from = sourceLocation, transfer.style, to = destinationLocation)
            StyleTransferMode.MOVE ->
                styleRepository.moveStyle(userId, from = sourceLocation, transfer.style, to = destinationLocation)
        }
        when (result) {
            is Result.Success ->
                _events.send(
                    StyleGalleryEvent.StyleTransferred(
                        mode = transfer.mode,
                        target = target,
                        destinationFolderId = folderId,
                    )
                )
            is Result.Error ->
                _state.update { it.copy(errorMessage = result.error.toStyleUiText()) }
        }
    }

    private fun observeStyles() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            val tier = entitlements.awaitHydrated().tier
            val limits = if (customerId == null) {
                StyleCollectionLimits.forInspiration(tier)
            } else {
                StyleCollectionLimits.forCustomer(tier)
            }
            val cap = if (!limits.foldersEnabled) limits.flatCap else limits.maxImagesPerFolder
            if (!limits.foldersEnabled) observeFlattened(userId, cap) else observePerFolder(userId, cap)
        }
    }

    private suspend fun observeFlattened(userId: String, cap: Int) {
        foldersWithStylesFlow(userId)
            .map { folders ->
                folders
                    .flatMap { folder -> folder.styles.map { it to folder.folderId } }
                    .sortedByDescending { it.first.createdAt }
            }
            .collect { pairs ->
                entryLocations = pairs.associate { (style, folderId) -> style.id to locationFor(folderId) }
                val ordered = pairs.map { it.first }
                _state.update {
                    it.copy(
                        styles = ordered,
                        lockedStyleIds = StyleLockPolicy.lockedStyleIds(ordered, cap),
                        isLoading = false,
                    )
                }
            }
    }

    private suspend fun observePerFolder(userId: String, cap: Int) {
        styleRepository.observeStyles(userId, location).collect { result ->
            when (result) {
                is Result.Success -> {
                    val ordered = result.data.sortedByDescending { it.createdAt }
                    entryLocations = ordered.associate { it.id to location }
                    _state.update {
                        it.copy(
                            styles = ordered,
                            lockedStyleIds = StyleLockPolicy.lockedStyleIds(ordered, cap),
                            isLoading = false,
                        )
                    }
                }
                is Result.Error -> _state.update {
                    it.copy(isLoading = false, errorMessage = result.error.toStyleUiText())
                }
            }
        }
    }

    private fun foldersWithStylesFlow(userId: String) =
        when (val loc = location) {
            is StyleLocation.CustomerCloset ->
                styleRepository.observeFoldersWithStyles(userId, StyleLocation.CustomerCloset(loc.customerId))
            is StyleLocation.Inspiration ->
                styleRepository.observeFoldersWithStyles(userId, StyleLocation.Inspiration())
        }

    private fun locationFor(folderId: String?): StyleLocation = when (val loc = location) {
        is StyleLocation.CustomerCloset -> StyleLocation.CustomerCloset(loc.customerId, folderId)
        is StyleLocation.Inspiration -> StyleLocation.Inspiration(folderId)
    }

    // The true location of a loaded style (its folder), falling back to the gallery location.
    private fun locationOf(styleId: String): StyleLocation = entryLocations[styleId] ?: location

    private fun deleteStyle() {
        val style = _state.value.styleToDelete ?: return
        _state.update { it.copy(showDeleteDialog = false, styleToDelete = null) }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            val result = styleRepository.deleteStyle(userId, locationOf(style.id), style)
            if (result is Result.Error) {
                _state.update { it.copy(errorMessage = result.error.toStyleUiText()) }
            }
        }
    }
}
