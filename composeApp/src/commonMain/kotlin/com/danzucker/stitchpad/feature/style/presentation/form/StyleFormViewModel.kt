package com.danzucker.stitchpad.feature.style.presentation.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.StyleLocation
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.core.domain.repository.StyleRepository
import com.danzucker.stitchpad.core.media.ImageCompressor
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.style.domain.StyleCollectionLimits
import com.danzucker.stitchpad.feature.style.domain.StyleError
import com.danzucker.stitchpad.feature.style.domain.StyleLockPolicy
import com.danzucker.stitchpad.feature.style.domain.countStylesAcrossFolders
import com.danzucker.stitchpad.feature.style.presentation.cap.StyleCapKind
import com.danzucker.stitchpad.feature.style.presentation.cap.styleCapInfo
import com.danzucker.stitchpad.feature.style.presentation.toStyleUiText
import com.danzucker.stitchpad.feature.style.presentation.toUiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.style_action_verify_failed

private const val MAX_PHOTO_SIZE_BYTES: Int = 5 * 1024 * 1024

class StyleFormViewModel(
    savedStateHandle: SavedStateHandle,
    private val styleRepository: StyleRepository,
    private val authRepository: AuthRepository,
    private val orderRepository: OrderRepository,
    private val entitlements: EntitlementsProvider,
    private val imageCompressor: ImageCompressor,
) : ViewModel() {

    private val customerId: String? = savedStateHandle["customerId"]
    private val styleId: String? = savedStateHandle["styleId"]
    private val linkToOrderId: String? = savedStateHandle["linkToOrderId"]
    private val linkToItemId: String? = savedStateHandle["linkToItemId"]
    private val folderId: String? = savedStateHandle["folderId"]
    private val readOnly: Boolean = savedStateHandle["readOnly"] ?: false

    private val location: StyleLocation =
        customerId?.let { StyleLocation.CustomerCloset(it, folderId) } ?: StyleLocation.Inspiration(folderId)

    // Multi-pick only when adding to a closet — not when editing one style and
    // not when attaching exactly one style to an order (the link flow).
    private val allowMultiPhoto: Boolean = styleId == null && linkToOrderId == null

    private var hasLoadedInitialData = false
    private val _state = MutableStateFlow(
        StyleFormState(
            isEditMode = styleId != null,
            allowMultiPhoto = allowMultiPhoto,
            readOnly = readOnly,
        )
    )

    private val _events = Channel<StyleFormEvent>()
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                hasLoadedInitialData = true
                if (styleId != null) loadStyle(styleId) else if (allowMultiPhoto) computeMaxPhotoSelection()
                observeUnlockOnUpgrade()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = StyleFormState(
                isEditMode = styleId != null,
                allowMultiPhoto = allowMultiPhoto,
                readOnly = readOnly,
            )
        )

    fun onAction(action: StyleFormAction) {
        when (action) {
            is StyleFormAction.OnPhotosPicked -> onPhotosPicked(action.photos)
            is StyleFormAction.OnRemovePhoto -> {
                if (_state.value.readOnly) return
                _state.update {
                    it.copy(selectedPhotos = it.selectedPhotos.filterNot { p -> p === action.photo })
                }
            }
            StyleFormAction.OnSaveClick -> save()
            StyleFormAction.OnNavigateBack -> {
                viewModelScope.launch { _events.send(StyleFormEvent.NavigateBack) }
            }
            StyleFormAction.OnErrorDismiss -> {
                _state.update { it.copy(errorMessage = null) }
            }
            StyleFormAction.OnDismissCapSheet -> _state.update { it.copy(capSheet = null) }
            StyleFormAction.OnUpgradeFromCap -> {
                _state.update { it.copy(capSheet = null) }
                viewModelScope.launch { _events.send(StyleFormEvent.NavigateToUpgrade) }
            }
        }
    }

    // Tracks the in-flight gallery-pick compression so save() can await it and a
    // newer pick can supersede an older one (latest-wins).
    private var photoProcessingJob: Job? = null

    private fun onPhotosPicked(photos: List<ByteArray>) {
        if (photos.isEmpty()) return
        photoProcessingJob?.cancel()
        photoProcessingJob = viewModelScope.launch {
            // Gallery picks arrive at full resolution; downscale each before the size
            // guard so a normal phone photo is accepted instead of rejected. A decode
            // failure (null) falls back to the original bytes and lets the guard decide.
            val processed = photos.map { imageCompressor.compress(it) ?: it }
            // Reject the whole new batch if any image still exceeds the cap after
            // compression. The existing selection is preserved so the user doesn't lose
            // previously accepted photos.
            if (processed.any { it.size > MAX_PHOTO_SIZE_BYTES }) {
                _state.update { it.copy(errorMessage = StyleError.PHOTO_TOO_LARGE.toUiText()) }
                return@launch
            }
            // Multi-photo closet add: append and trim to cap.
            // Single/edit/order-link: replace entirely so "tap to change" swaps the photo.
            _state.update { current ->
                val merged = if (current.allowMultiPhoto) {
                    (current.selectedPhotos + processed).take(current.maxPhotoSelection)
                } else {
                    processed
                }
                current.copy(selectedPhotos = merged, errorMessage = null)
            }
        }
    }

    private fun observeUnlockOnUpgrade() {
        if (!readOnly) return
        viewModelScope.launch {
            entitlements.awaitHydrated()
            entitlements.flow
                .map { it.tier }
                .distinctUntilChanged()
                .collect { tier ->
                    if (!isStyleLockedForTier(tier)) {
                        _state.update { it.copy(readOnly = false) }
                    }
                }
        }
    }

    @Suppress("ReturnCount")
    private suspend fun isStyleLockedForTier(tier: SubscriptionTier): Boolean {
        val limits = if (customerId == null) {
            StyleCollectionLimits.forInspiration(tier)
        } else {
            StyleCollectionLimits.forCustomer(tier)
        }
        // Free (no folders): the form was opened read-only from a locked context and no
        // upgrade has changed that — keep it locked. (Only paid tiers can unlock.)
        if (!limits.foldersEnabled) return true
        val userId = authRepository.getCurrentUser()?.id ?: return true // can't tell -> fail safe: stay locked
        val targetStyleId = styleId ?: return false
        // `location` already targets this style's true folder (nav passes the real folderId).
        val folderStyles = when (val r = styleRepository.observeStyles(userId, location).first()) {
            is Result.Success -> r.data
            is Result.Error -> return true // can't determine -> stay locked (fail safe)
        }
        return StyleLockPolicy.lockedStyleIds(folderStyles, limits.maxImagesPerFolder)
            .contains(targetStyleId)
    }

    private fun loadStyle(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val userId = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            when (val result = styleRepository.observeStyles(userId, location).first()) {
                is Result.Success -> {
                    val style = result.data.find { it.id == id }
                    if (style != null) {
                        _state.update {
                            it.copy(
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

    /**
     * Clamp the multi-pick limit to the folder's remaining capacity so a Pro user
     * with a 5-image folder can't pick 10 only to be blocked at save. Remaining =
     * cap − current count, coerced into [1, ceiling] (the gallery blocks entry at 0).
     *
     * On Free tier (folders disabled) the count spans the entire closet/library so
     * that photos added across different folders all count toward the flat cap.
     *
     * This is a non-critical / best-effort path: if the count can't be read (null),
     * we fall back to 0 so the picker still functions. The authoritative guard is in
     * the save path below.
     */
    private fun computeMaxPhotoSelection() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            val cap = resolveImageCap()
            val current = currentClosetCount(userId) ?: 0
            val remaining = (cap - current).coerceIn(1, STYLE_MULTI_PICK_CEILING)
            _state.update { it.copy(maxPhotoSelection = remaining) }
        }
    }

    /**
     * Returns the total style count to compare against the image cap, or `null` if a
     * read error prevents the count from being determined.
     *
     * When folders are disabled (Free tier), counts the ENTIRE closet/library across
     * all folders so the flat cap is enforced globally (returns null on any sub-read
     * error). When folders are enabled (Pro/Atelier), counts only the current location
     * (a single folder); returns 0 on read error because the paid path has its own
     * explicit error handling in the save guard.
     */
    private suspend fun currentClosetCount(userId: String): Int? {
        val tier = entitlements.awaitHydrated().tier
        val foldersEnabled = if (customerId == null) {
            StyleCollectionLimits.forInspiration(tier).foldersEnabled
        } else {
            StyleCollectionLimits.forCustomer(tier).foldersEnabled
        }
        if (foldersEnabled) {
            return when (val r = styleRepository.observeStyles(userId, location).first()) {
                is Result.Success -> r.data.size
                is Result.Error -> 0
            }
        }
        // Free tier: count the entire flattened closet/library across all folders.
        // Returns null if any sub-read fails (callers must fail closed for hard guards).
        val root = when (val loc = location) {
            is StyleLocation.CustomerCloset -> StyleLocation.CustomerCloset(loc.customerId)
            is StyleLocation.Inspiration -> StyleLocation.Inspiration()
        }
        return styleRepository.countStylesAcrossFolders(userId, root)
    }

    // Resolves the image cap from the CURRENT tier each call (awaiting hydration).
    // Not cached: a tier change while this VM is alive must reflect the new cap.
    private suspend fun resolveImageCap(): Int {
        val tier = entitlements.awaitHydrated().tier
        val limits = if (customerId == null) {
            StyleCollectionLimits.forInspiration(tier)
        } else {
            StyleCollectionLimits.forCustomer(tier)
        }
        return if (!limits.foldersEnabled) limits.flatCap else limits.maxImagesPerFolder
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    private fun save() {
        if (_state.value.readOnly) {
            viewModelScope.launch { _events.send(StyleFormEvent.NavigateToUpgrade) }
            return
        }
        // If a just-picked photo is still compressing, wait for it before snapshotting
        // state, then re-enter — otherwise the new photo would be dropped.
        val pendingPhotos = photoProcessingJob
        if (pendingPhotos?.isActive == true) {
            viewModelScope.launch {
                pendingPhotos.join()
                save()
            }
            return
        }
        val s = _state.value
        // Only a photo (create) or a loaded style (edit) is required,
        // so we never persist a fully empty entry.
        val missingPhotoForCreate = !s.isEditMode && s.selectedPhotos.isEmpty()
        val missingStyleForEdit = s.isEditMode && s.existingStyle == null
        if (missingPhotoForCreate || missingStyleForEdit) return

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val userId = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(isSaving = false) }
                return@launch
            }
            if (s.isEditMode && s.existingStyle != null) {
                val updateResult = styleRepository.updateStyle(
                    userId = userId,
                    location = location,
                    style = s.existingStyle,
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

            // Cap check: only applies to create paths (not edit).
            // Await hydration here so a cold-start Pro user isn't wrongly blocked.
            val tier = entitlements.awaitHydrated().tier
            val limits = if (customerId == null) {
                StyleCollectionLimits.forInspiration(tier)
            } else {
                StyleCollectionLimits.forCustomer(tier)
            }
            val imageCap = if (!limits.foldersEnabled) limits.flatCap else limits.maxImagesPerFolder
            // FIX 7(form) + flattened-Free fix: on Free tier count the whole closet/library
            // across all folders (flat cap applies globally). On paid tiers count the single
            // current folder. Fail CLOSED on count-read error — block the create rather than
            // treating an unreadable count as 0 (which bypasses the cap).
            val current: Int
            if (!limits.foldersEnabled) {
                // Free: flattened count; null = sub-read failed → abort (fail closed).
                val flatCount = currentClosetCount(userId)
                if (flatCount == null) {
                    _state.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = UiText.StringResourceText(Res.string.style_action_verify_failed)
                        )
                    }
                    return@launch
                }
                current = flatCount
            } else {
                // Paid: single-location count; hard-fail on read error.
                when (val r = styleRepository.observeStyles(userId, location).first()) {
                    is Result.Success -> current = r.data.size
                    is Result.Error -> {
                        _state.update {
                            it.copy(
                                isSaving = false,
                                errorMessage = UiText.StringResourceText(Res.string.style_action_verify_failed)
                            )
                        }
                        return@launch
                    }
                }
            }
            if (current + s.selectedPhotos.size > imageCap) {
                _state.update {
                    it.copy(
                        isSaving = false,
                        capSheet = styleCapInfo(StyleCapKind.STYLES, tier, isInspiration = customerId == null)
                    )
                }
                return@launch
            }

            // Multi-photo closet add: batch-create and return — the order-link
            // path below is unreachable here (allowMultiPhoto requires no link).
            if (s.selectedPhotos.size > 1) {
                val batchResult = styleRepository.createStyles(
                    userId = userId,
                    location = location,
                    description = "",
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
                location = location,
                description = "",
                photoBytes = s.selectedPhotos.firstOrNull() ?: ByteArray(0),
            )
            if (createResult is Result.Error) {
                _state.update {
                    it.copy(isSaving = false, errorMessage = createResult.error.toStyleUiText())
                }
                return@launch
            }
            val newStyleId = (createResult as Result.Success).data

            // If opened from an order's "link" path, attach this style to the garment it was
            // created from ([linkToItemId]; null falls back to items[0]). Mirror the
            // measurement-link flow: failure here is silent — style is already persisted; user
            // can retry from the order detail picker. linkStyleToOrderItems guards dup + cap.
            val linkOrderId = linkToOrderId
            if (linkOrderId != null) {
                when (val orderResult = orderRepository.getOrder(userId, linkOrderId)) {
                    is Result.Success -> {
                        val order = orderResult.data
                        val updatedItems = linkStyleToOrderItems(
                            items = order.items,
                            targetItemId = linkToItemId,
                            newStyleId = newStyleId,
                        )
                        if (updatedItems != null) {
                            orderRepository.updateOrder(userId, order.copy(items = updatedItems))
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
