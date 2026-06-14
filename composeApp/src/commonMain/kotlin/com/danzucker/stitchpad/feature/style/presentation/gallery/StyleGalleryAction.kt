package com.danzucker.stitchpad.feature.style.presentation.gallery

import com.danzucker.stitchpad.core.domain.model.Style

sealed interface StyleGalleryAction {
    data object OnAddClick : StyleGalleryAction
    data class OnStyleClick(val style: Style) : StyleGalleryAction
    data class OnStyleLongPress(val style: Style) : StyleGalleryAction
    data object OnDismissActionSheet : StyleGalleryAction
    data object OnCopyClick : StyleGalleryAction
    data object OnMoveClick : StyleGalleryAction
    data class OnTargetCustomerSelected(val customerId: String) : StyleGalleryAction
    data class OnDestinationFolderSelected(val folderId: String?) : StyleGalleryAction
    data object OnDismissTransfer : StyleGalleryAction
    data class OnDeleteClick(val style: Style) : StyleGalleryAction
    data object OnConfirmDelete : StyleGalleryAction
    data object OnDismissDeleteDialog : StyleGalleryAction
    data object OnNavigateBack : StyleGalleryAction
    data object OnErrorDismiss : StyleGalleryAction
}
