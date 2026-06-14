package com.danzucker.stitchpad.feature.style.presentation.gallery

sealed interface StyleGalleryEvent {
    data object NavigateBack : StyleGalleryEvent
    data class NavigateToAddStyle(val customerId: String?, val folderId: String?) : StyleGalleryEvent
    data class NavigateToEditStyle(
        val customerId: String?,
        val folderId: String?,
        val styleId: String
    ) : StyleGalleryEvent
    data class StyleTransferred(
        val mode: StyleTransferMode,
        val target: TransferTarget,
    ) : StyleGalleryEvent
    data class CapReached(val cap: Int) : StyleGalleryEvent
    data object NavigateToUpgrade : StyleGalleryEvent
}
