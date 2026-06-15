package com.danzucker.stitchpad.feature.style.presentation.folders

sealed interface StyleFoldersEvent {
    data object NavigateBack : StyleFoldersEvent
    data class NavigateToFolder(val customerId: String?, val folderId: String?) : StyleFoldersEvent

    /**
     * Free users (foldersEnabled=false) are forwarded straight to the flat default
     * gallery, replacing this folders screen in the back stack so Back doesn't loop
     * back to a screen they never meaningfully saw.
     */
    data class RedirectToFlatGallery(val customerId: String?) : StyleFoldersEvent
    data object NavigateToUpgrade : StyleFoldersEvent
}
