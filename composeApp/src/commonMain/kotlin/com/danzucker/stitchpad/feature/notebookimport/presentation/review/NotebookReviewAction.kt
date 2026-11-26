package com.danzucker.stitchpad.feature.notebookimport.presentation.review

import com.danzucker.stitchpad.feature.notebookimport.domain.MergeNoteLabels

sealed interface NotebookReviewAction {
    data class OnCardClick(val cardId: String) : NotebookReviewAction
    data class OnConfirmMatch(val cardId: String) : NotebookReviewAction
    data class OnRejectMatch(val cardId: String) : NotebookReviewAction

    /** [labels] are the localized note labels for a clashing date or phone from the other card. */
    data class OnMergeClick(val cardIds: List<String>, val labels: MergeNoteLabels) : NotebookReviewAction
    data class OnKeepSeparateClick(val cardIds: List<String>) : NotebookReviewAction
    data object OnSaveAllClick : NotebookReviewAction
    data object OnDiscardClick : NotebookReviewAction
    data object OnDiscardDismiss : NotebookReviewAction
    data object OnDiscardConfirm : NotebookReviewAction
    data object OnNavigateBack : NotebookReviewAction
    data object OnMessageShown : NotebookReviewAction
    data object OnCapSheetDismiss : NotebookReviewAction
}
