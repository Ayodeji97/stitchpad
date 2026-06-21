package com.danzucker.stitchpad.feature.style.presentation.gallery

import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.core.domain.model.StyleLocation
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.style.presentation.cap.StyleCapInfo

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

/**
 * A destination folder option shown in the second step of the copy/move flow.
 * [folderId] null = the destination&apos;s default "My styles" folder.
 * [name] null = the default folder (UI uses the localised default name).
 */
data class TransferFolderOption(
    val folderId: String?,
    val name: String?,
    val count: Int,
    val cap: Int,
) {
    val isFull: Boolean get() = count >= cap
}

/** An in-progress copy/move: the style being transferred + selectable targets. */
data class StyleTransfer(
    val style: Style,
    val mode: StyleTransferMode,
    val targets: List<TransferTarget>,
    val selectedTarget: TransferTarget? = null,
    /** null = still on the target-pick step; non-null = folder-pick step. */
    val destinationFolders: List<TransferFolderOption>? = null,
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
    val errorMessage: UiText? = null,
    /** Non-null while the cap-reached upgrade sheet should be shown. */
    val capSheet: StyleCapInfo? = null,
    /** Ids of styles shown read-only (over the current tier cap). Empty on paid within cap. */
    val lockedStyleIds: Set<String> = emptySet(),
    /** Style whose optional-title editor sheet is open, or null when closed. */
    val titleEditTarget: Style? = null,
)
