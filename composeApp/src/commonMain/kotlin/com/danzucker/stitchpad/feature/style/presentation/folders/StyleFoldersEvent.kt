package com.danzucker.stitchpad.feature.style.presentation.folders

sealed interface StyleFoldersEvent {
    data object NavigateBack : StyleFoldersEvent
    data class NavigateToFolder(val customerId: String?, val folderId: String?) : StyleFoldersEvent
    data object NavigateToUpgrade : StyleFoldersEvent
}
