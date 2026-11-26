package com.danzucker.stitchpad.feature.notebookimport.presentation.review

sealed interface NotebookReviewEvent {
    data object NavigateBack : NotebookReviewEvent
    data class NavigateToCard(val jobId: String, val cardId: String) : NotebookReviewEvent
}
