package com.danzucker.stitchpad.feature.notebookimport.presentation.card

sealed interface NotebookCardEvent {
    data object NavigateBack : NotebookCardEvent

    /** "Save now" stopped at the Free customer cap: open the existing cap sheet and upgrade path. */
    data class ShowCapReachedSheet(val activeCount: Int, val customerCap: Int) : NotebookCardEvent
}
