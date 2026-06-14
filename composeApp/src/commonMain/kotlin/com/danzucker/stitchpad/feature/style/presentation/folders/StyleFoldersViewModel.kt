package com.danzucker.stitchpad.feature.style.presentation.folders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.StyleLocation
import com.danzucker.stitchpad.core.domain.repository.StyleRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.style.domain.StyleCollectionLimits
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

class StyleFoldersViewModel(
    savedStateHandle: SavedStateHandle,
    private val styleRepository: StyleRepository,
    private val authRepository: AuthRepository,
    private val entitlements: EntitlementsProvider,
) : ViewModel() {

    private val customerId: String? = savedStateHandle["customerId"]

    private val location: StyleLocation =
        customerId?.let(StyleLocation::CustomerCloset) ?: StyleLocation.Inspiration()

    private val limits: StyleCollectionLimits =
        if (customerId == null) {
            StyleCollectionLimits.forInspiration(entitlements.current().tier)
        } else {
            StyleCollectionLimits.forCustomer(entitlements.current().tier)
        }

    private var hasLoadedInitialData = false

    private val _state = MutableStateFlow(
        StyleFoldersState(
            isInspiration = customerId == null,
            limits = limits,
        )
    )

    private val _events = Channel<StyleFoldersEvent>()
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                hasLoadedInitialData = true
                // Free users with foldersEnabled=false are immediately forwarded to the
                // flat default gallery — no folder UI shown.
                if (!limits.foldersEnabled) {
                    viewModelScope.launch {
                        _events.send(StyleFoldersEvent.RedirectToFlatGallery(customerId))
                    }
                } else {
                    observeFolders()
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = StyleFoldersState(
                isInspiration = customerId == null,
                limits = limits,
            )
        )

    @Suppress("CyclomaticComplexMethod")
    fun onAction(action: StyleFoldersAction) {
        when (action) {
            is StyleFoldersAction.OnFolderClick ->
                viewModelScope.launch { _events.send(StyleFoldersEvent.NavigateToFolder(customerId, action.folderId)) }
            StyleFoldersAction.OnCreateClick -> handleCreateClick()
            is StyleFoldersAction.OnConfirmCreate -> handleConfirmCreate(action.name)
            is StyleFoldersAction.OnRenameClick -> _state.update { it.copy(renameTarget = action.folder) }
            is StyleFoldersAction.OnConfirmRename -> handleConfirmRename(action.name)
            is StyleFoldersAction.OnDeleteClick -> _state.update { it.copy(deleteTarget = action.folder) }
            StyleFoldersAction.OnConfirmDelete -> handleConfirmDelete()
            StyleFoldersAction.OnDismissSheet ->
                _state.update { it.copy(showCreateSheet = false, renameTarget = null, deleteTarget = null) }
            StyleFoldersAction.OnUpgradeClick ->
                viewModelScope.launch { _events.send(StyleFoldersEvent.NavigateToUpgrade) }
            StyleFoldersAction.OnNavigateBack ->
                viewModelScope.launch { _events.send(StyleFoldersEvent.NavigateBack) }
            StyleFoldersAction.OnErrorDismiss -> _state.update { it.copy(errorMessage = null) }
        }
    }

    private fun handleCreateClick() {
        // The default "My styles" folder counts as one of maxFolders.
        // state.folders contains only named folders, so total = folders.size + 1.
        if (_state.value.folders.size + 1 >= limits.maxFolders) {
            viewModelScope.launch { _events.send(StyleFoldersEvent.NavigateToUpgrade) }
        } else {
            _state.update { it.copy(showCreateSheet = true) }
        }
    }

    private fun handleConfirmCreate(rawName: String) {
        val name = rawName.trim()
        if (name.isBlank()) return
        _state.update { it.copy(showCreateSheet = false) }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            val result = styleRepository.createFolder(userId, location, name)
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
            val result = styleRepository.renameFolder(userId, location, target.id, name)
            if (result is Result.Error) {
                _state.update { it.copy(errorMessage = result.error.toStyleUiText()) }
            }
        }
    }

    private fun handleConfirmDelete() {
        val target = _state.value.deleteTarget ?: return
        _state.update { it.copy(deleteTarget = null) }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            val result = styleRepository.deleteFolder(userId, location, target.id)
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
            styleRepository.observeFolders(userId, location).collect { result ->
                when (result) {
                    is Result.Success -> _state.update {
                        it.copy(folders = result.data, isLoading = false)
                    }
                    is Result.Error -> _state.update {
                        it.copy(isLoading = false, errorMessage = result.error.toStyleUiText())
                    }
                }
            }
        }
    }
}
