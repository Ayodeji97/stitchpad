package com.danzucker.stitchpad.feature.notebookimport.presentation.review

sealed interface NotebookReviewEvent {
    data object NavigateBack : NotebookReviewEvent
    data class NavigateToCard(val jobId: String, val cardId: String) : NotebookReviewEvent

    /** Saving stopped at the Free customer cap: open the existing cap sheet and upgrade path. */
    data class ShowCapReachedSheet(val activeCount: Int, val customerCap: Int) : NotebookReviewEvent
}
