package com.danzucker.stitchpad.feature.style.presentation.gallery

sealed interface StyleGalleryEvent {
    data object NavigateBack : StyleGalleryEvent
    data class NavigateToAddStyle(val customerId: String) : StyleGalleryEvent
    data class NavigateToEditStyle(val customerId: String, val styleId: String) : StyleGalleryEvent
}
