package com.danzucker.stitchpad.feature.style.presentation.folders

import com.danzucker.stitchpad.core.domain.model.StyleFolder

sealed interface StyleFoldersAction {
    /** Tap on a folder card. [folderId] is null for the default "My styles" folder. */
    data class OnFolderClick(val folderId: String?) : StyleFoldersAction

    /** Long-press on a named folder card — opens the rename/delete action sheet. */
    data class OnFolderLongPress(val folder: StyleFolder) : StyleFoldersAction

    /** Dismiss the long-press action sheet without taking any action. */
    data object OnDismissFolderActionSheet : StyleFoldersAction
    data object OnCreateClick : StyleFoldersAction
    data class OnConfirmCreate(val name: String) : StyleFoldersAction
    data class OnRenameClick(val folder: StyleFolder) : StyleFoldersAction
    data class OnConfirmRename(val name: String) : StyleFoldersAction
    data class OnDeleteClick(val folder: StyleFolder) : StyleFoldersAction
    data object OnConfirmDelete : StyleFoldersAction
    data object OnDismissSheet : StyleFoldersAction
    data object OnUpgradeClick : StyleFoldersAction
    data object OnNavigateBack : StyleFoldersAction
    data object OnErrorDismiss : StyleFoldersAction
}
