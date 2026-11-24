package com.danzucker.stitchpad.feature.notebookimport.presentation.review

sealed interface NotebookReviewAction {
    data class OnCardClick(val cardId: String) : NotebookReviewAction
    data class OnConfirmMatch(val cardId: String) : NotebookReviewAction
    data class OnRejectMatch(val cardId: String) : NotebookReviewAction

    /** [dateNoteLabel] is the localized "Date" label, for a second written date kept as a note. */
    data class OnMergeClick(val cardIds: List<String>, val dateNoteLabel: String) : NotebookReviewAction
    data class OnKeepSeparateClick(val cardIds: List<String>) : NotebookReviewAction
    data object OnSaveAllClick : NotebookReviewAction
    data object OnDiscardClick : NotebookReviewAction
    data object OnDiscardDismiss : NotebookReviewAction
    data object OnDiscardConfirm : NotebookReviewAction
    data object OnNavigateBack : NotebookReviewAction
    data object OnMessageShown : NotebookReviewAction
}
