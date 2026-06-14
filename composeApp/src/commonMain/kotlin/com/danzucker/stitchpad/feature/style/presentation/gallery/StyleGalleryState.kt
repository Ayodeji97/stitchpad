package com.danzucker.stitchpad.feature.style.presentation.gallery

import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.core.domain.model.StyleLocation
import com.danzucker.stitchpad.core.presentation.UiText

enum class StyleTransferMode { COPY, MOVE }

/** A place the user can copy/move a style into. */
sealed interface TransferTarget {
    val id: String
    val location: StyleLocation

    data class Customer(
        val customerId: String,
        val name: String,
    ) : TransferTarget {
        override val id: String = customerId
        override val location: StyleLocation = StyleLocation.CustomerCloset(customerId)
    }

    data object Inspiration : TransferTarget {
        override val id: String = "inspiration"
        override val location: StyleLocation = StyleLocation.Inspiration()
    }
}

/** An in-progress copy/move: the style being transferred + selectable targets. */
data class StyleTransfer(
    val style: Style,
    val mode: StyleTransferMode,
    val targets: List<TransferTarget>,
)

data class StyleGalleryState(
    val styles: List<Style> = emptyList(),
    val isLoading: Boolean = true,
    val isInspirationGallery: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val styleToDelete: Style? = null,
    /** Long-pressed style whose actions sheet (Copy / Move / Delete) is open. */
    val actionSheetStyle: Style? = null,
    /** Active copy/move flow with its customer picker, or null when closed. */
    val transfer: StyleTransfer? = null,
    val errorMessage: UiText? = null
)
