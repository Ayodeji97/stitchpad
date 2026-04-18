package com.danzucker.stitchpad.feature.style.presentation.gallery

import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.core.presentation.UiText

data class StyleGalleryState(
    val styles: List<Style> = emptyList(),
    val isLoading: Boolean = true,
    val showDeleteDialog: Boolean = false,
    val styleToDelete: Style? = null,
    val errorMessage: UiText? = null
)
