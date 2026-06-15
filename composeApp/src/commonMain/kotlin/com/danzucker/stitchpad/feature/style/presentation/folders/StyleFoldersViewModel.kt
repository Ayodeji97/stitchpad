package com.danzucker.stitchpad.feature.style.presentation.folders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.core.domain.model.StyleFolder
import com.danzucker.stitchpad.core.domain.model.StyleLocation
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.StyleRepository
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.style.domain.StyleCollectionLimits
import com.danzucker.stitchpad.feature.style.presentation.cap.StyleCapKind
import com.danzucker.stitchpad.feature.style.presentation.cap.styleCapInfo
import com.danzucker.stitchpad.feature.style.presentation.toStyleUiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.style_action_verify_failed
import stitchpad.composeapp.generated.resources.style_folder_duplicate_name

@Suppress("TooManyFunctions")
@OptIn(ExperimentalCoroutinesApi::class)
class StyleFoldersViewModel(
    savedStateHandle: SavedStateHandle,
    private val styleRepository: StyleRepository,
    private val customerRepository: CustomerRepository,
    private val authRepository: AuthRepository,
    private val entitlements: EntitlementsProvider,
) : ViewModel() {

    private val customerId: String? = savedStateHandle["customerId"]

    // Root location — the default (null-folderId) folder of this context.
    private val rootLocation: StyleLocation =
        customerId?.let(StyleLocation::CustomerCloset) ?: StyleLocation.Inspiration()

    private var hasLoadedInitialData = false

    private val _state = MutableStateFlow(
        StyleFoldersState(isInspiration = customerId == null)
    )

    private val _events = Channel<StyleFoldersEvent>()
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                hasLoadedInitialData = true
                onStart()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = StyleFoldersState(isInspiration = customerId == null),
        )

    @Suppress("CyclomaticComplexMethod")
    fun onAction(action: StyleFoldersAction) {
        when (action) {
            is StyleFoldersAction.OnFolderClick ->
                viewModelScope.launch { _events.send(StyleFoldersEvent.NavigateToFolder(customerId, action.folderId)) }
            is StyleFoldersAction.OnFolderLongPress ->
                _state.update { it.copy(actionSheetFolder = action.folder) }
            StyleFoldersAction.OnDismissFolderActionSheet ->
                _state.update { it.copy(actionSheetFolder = null) }
            StyleFoldersAction.OnCreateClick -> handleCreateClick()
            is StyleFoldersAction.OnConfirmCreate -> handleConfirmCreate(action.name)
            is StyleFoldersAction.OnRenameClick ->
                _state.update { it.copy(renameTarget = action.folder, actionSheetFolder = null) }
            is StyleFoldersAction.OnConfirmRename -> handleConfirmRename(action.name)
            is StyleFoldersAction.OnDeleteClick ->
                _state.update { it.copy(deleteTarget = action.folder, actionSheetFolder = null) }
            StyleFoldersAction.OnConfirmDelete -> handleConfirmDelete()
            StyleFoldersAction.OnDismissSheet ->
                _state.update { it.copy(showCreateSheet = false, renameTarget = null, deleteTarget = null) }
            StyleFoldersAction.OnUpgradeClick ->
                viewModelScope.launch { _events.send(StyleFoldersEvent.NavigateToUpgrade) }
            StyleFoldersAction.OnNavigateBack ->
                viewModelScope.launch { _events.send(StyleFoldersEvent.NavigateBack) }
            StyleFoldersAction.OnErrorDismiss -> _state.update { it.copy(errorMessage = null) }
            StyleFoldersAction.OnDismissCapSheet -> _state.update { it.copy(capSheet = null) }
            StyleFoldersAction.OnUpgradeFromCap -> {
                _state.update { it.copy(capSheet = null) }
                viewModelScope.launch { _events.send(StyleFoldersEvent.NavigateToUpgrade) }
            }
        }
    }

    private fun onStart() {
        viewModelScope.launch {
            // Always await hydration first so Free/Pro/Atelier is resolved correctly
            // before the redirect-or-observe decision.
            val limits = resolveLimits()
            _state.update { it.copy(limits = limits) }

            if (!limits.foldersEnabled) {
                // Free users with foldersEnabled=false are immediately forwarded to the
                // flat default gallery — no folder UI shown.
                _state.update { it.copy(isLoading = false) }
                _events.send(StyleFoldersEvent.RedirectToFlatGallery(customerId))
            } else {
                if (customerId != null) {
                    loadCustomerName(customerId)
                }
                observeFolders()
            }
        }
    }

    // Resolves limits from the CURRENT tier each call (awaiting hydration). Not cached:
    // a tier change while this VM is alive must reflect the new limits.
    private suspend fun resolveLimits(): StyleCollectionLimits {
        val tier = entitlements.awaitHydrated().tier
        return if (customerId == null) {
            StyleCollectionLimits.forInspiration(tier)
        } else {
            StyleCollectionLimits.forCustomer(tier)
        }
    }

    // FIX 2: Load the customer's name so the closet title ("Name's Closet") is correct.
    private suspend fun loadCustomerName(cid: String) {
        val userId = authRepository.getCurrentUser()?.id ?: return
        when (val r = customerRepository.observeCustomers(userId).first()) {
            is Result.Success -> {
                val name = r.data.firstOrNull { it.id == cid }?.name ?: return
                _state.update { it.copy(customerName = name) }
            }
            is Result.Error -> Unit // silent — title stays generic
        }
    }

    // The default "My styles" folder counts as one of maxFolders, so the total
    // folder count is namedFolderCount + 1.
    private fun atFolderCap(limits: StyleCollectionLimits): Boolean =
        _state.value.namedFolderCount + 1 >= limits.maxFolders

    private fun handleCreateClick() {
        viewModelScope.launch {
            val tier = entitlements.awaitHydrated().tier
            val limits = if (customerId == null) {
                StyleCollectionLimits.forInspiration(tier)
            } else {
                StyleCollectionLimits.forCustomer(tier)
            }
            if (atFolderCap(limits)) {
                _state.update {
                    it.copy(
                        capSheet = styleCapInfo(StyleCapKind.FOLDERS, tier, isInspiration = customerId == null)
                    )
                }
            } else {
                _state.update { it.copy(showCreateSheet = true) }
            }
        }
    }

    private fun handleConfirmCreate(rawName: String) {
        val name = rawName.trim()
        if (name.isBlank()) return
        _state.update { it.copy(showCreateSheet = false) }
        viewModelScope.launch {
            val tier = entitlements.awaitHydrated().tier
            val limits = if (customerId == null) {
                StyleCollectionLimits.forInspiration(tier)
            } else {
                StyleCollectionLimits.forCustomer(tier)
            }
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            // Re-read the AUTHORITATIVE folder list at confirm time. The FAB is
            // tappable while the grid is still loading (state.namedFolderCount == 0),
            // so the cached state can be stale; fetch live folders before creating.
            val liveFolders = liveFolders(userId)
            // FIX 1: if the live read failed, fail safe — block the create rather
            // than falling back to a possibly-stale (or zero) namedFolderCount.
            if (liveFolders == null) {
                _state.update {
                    it.copy(errorMessage = UiText.StringResourceText(Res.string.style_action_verify_failed))
                }
                return@launch
            }
            if (liveFolders.size + 1 >= limits.maxFolders) {
                _state.update {
                    it.copy(
                        capSheet = styleCapInfo(StyleCapKind.FOLDERS, tier, isInspiration = customerId == null)
                    )
                }
                return@launch
            }
            if (isDuplicateName(name, liveFolders, excludeId = null)) {
                showDuplicateNameError()
                return@launch
            }
            val result = styleRepository.createFolder(userId, rootLocation, name)
            if (result is Result.Error) {
                _state.update { it.copy(errorMessage = result.error.toStyleUiText()) }
            }
        }
    }

    private fun handleConfirmRename(rawName: String) {
        val target = _state.value.renameTarget ?: return
        val name = rawName.trim()
        if (name.isBlank()) return
        _state.update { it.copy(renameTarget = null) }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            // Fail closed: if the live folder list can't be read we can't verify
            // uniqueness, so surface the error rather than risk a duplicate name.
            val liveFolders = liveFolders(userId) ?: run {
                _state.update {
                    it.copy(errorMessage = UiText.StringResourceText(Res.string.style_action_verify_failed))
                }
                return@launch
            }
            if (isDuplicateName(name, liveFolders, excludeId = target.id)) {
                showDuplicateNameError()
                return@launch
            }
            val result = styleRepository.renameFolder(userId, rootLocation, target.id, name)
            if (result is Result.Error) {
                _state.update { it.copy(errorMessage = result.error.toStyleUiText()) }
            }
        }
    }

    private suspend fun liveFolders(userId: String): List<StyleFolder>? =
        when (val r = styleRepository.observeFolders(userId, rootLocation).first()) {
            is Result.Success -> r.data
            is Result.Error -> null
        }

    // Folder names must be unique within a collection (case-insensitive, trimmed).
    private fun isDuplicateName(name: String, folders: List<StyleFolder>?, excludeId: String?): Boolean =
        folders?.any { it.id != excludeId && it.name.trim().equals(name, ignoreCase = true) } == true

    private fun showDuplicateNameError() {
        _state.update { it.copy(errorMessage = UiText.StringResourceText(Res.string.style_folder_duplicate_name)) }
    }

    private fun handleConfirmDelete() {
        val target = _state.value.deleteTarget ?: return
        _state.update { it.copy(deleteTarget = null) }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            val result = styleRepository.deleteFolder(userId, rootLocation, target.id)
            if (result is Result.Error) {
                _state.update { it.copy(errorMessage = result.error.toStyleUiText()) }
            }
        }
    }

    private fun observeFolders() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }

            // Observe the named folders list, then flatMap to combine with a styles
            // flow for each folder + the default location so cards are always live.
            styleRepository.observeFolders(userId, rootLocation)
                .flatMapLatest { foldersResult ->
                    if (foldersResult is Result.Error) {
                        _state.update {
                            it.copy(isLoading = false, errorMessage = foldersResult.error.toStyleUiText())
                        }
                        // Return a flow that carries a null sentinel to signal no-op downstream.
                        return@flatMapLatest flowOf<Pair<List<FolderCardUi>, Int>?>(null)
                    }
                    val folders = (foldersResult as Result.Success).data

                    // Flow for default-location styles. keepingLastStyles retains the last
                    // successful list so a transient error doesn't flash a card to 0/blank cover.
                    val defaultStylesFlow = styleRepository.observeStyles(userId, rootLocation)
                        .keepingLastStyles()

                    // One flow per named folder, ordered by folder index.
                    val namedFlows = folders.map { folder ->
                        val loc = namedFolderLocation(folder)
                        styleRepository.observeStyles(userId, loc).keepingLastStyles()
                    }

                    // Combine default + all named into a single emission.
                    combine(
                        listOf(defaultStylesFlow) + namedFlows
                    ) { allStyles ->
                        val defaultStyles = allStyles[0]
                        val defaultCard = FolderCardUi(
                            folderId = null,
                            name = null,
                            count = defaultStyles.size,
                            coverUrl = cover(defaultStyles),
                            source = null,
                        )
                        val namedCards = folders.mapIndexed { idx, folder ->
                            val styles = allStyles[idx + 1]
                            FolderCardUi(
                                folderId = folder.id,
                                name = folder.name,
                                count = styles.size,
                                coverUrl = cover(styles),
                                source = folder,
                            )
                        }
                        (listOf(defaultCard) + namedCards) to folders.size
                    }.map { it as Pair<List<FolderCardUi>, Int>? }
                }
                .collect { result ->
                    if (result != null) {
                        val (cards, namedCount) = result
                        _state.update { it.copy(cards = cards, namedFolderCount = namedCount, isLoading = false) }
                    }
                }
        }
    }

    private fun namedFolderLocation(folder: StyleFolder): StyleLocation =
        if (customerId == null) {
            StyleLocation.Inspiration(folder.id)
        } else {
            StyleLocation.CustomerCloset(customerId, folder.id)
        }

    // Prefer the newest style's local file (pending upload / offline) over its
    // remote URL, mirroring how the gallery renders a thumbnail.
    private fun cover(styles: List<Style>): String? =
        styles.sortedByDescending { it.createdAt }
            .firstNotNullOfOrNull { style ->
                style.localPhotoPath ?: style.photoUrl.takeIf { it.isNotBlank() }
            }

    /** Retains the last successful style list so a transient error keeps the card live. */
    private fun Flow<Result<List<Style>, DataError.Network>>.keepingLastStyles(): Flow<List<Style>> =
        runningFold(emptyList()) { last, r -> if (r is Result.Success) r.data else last }
}
