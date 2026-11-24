package com.danzucker.stitchpad.feature.notebookimport.presentation.capture

sealed interface NotebookCaptureAction {
    /** Photos from the camera or the gallery, full size. They are compressed before upload. */
    class OnPhotosPicked(val photos: List<ByteArray>) : NotebookCaptureAction
    data class OnRetryPage(val pageKey: String) : NotebookCaptureAction
    data object OnReviewClick : NotebookCaptureAction
    data object OnNavigateBack : NotebookCaptureAction
    data object OnErrorDismiss : NotebookCaptureAction

    /** Runs the open-job look-up again after it failed or timed out. */
    data object OnRetryLookup : NotebookCaptureAction

    /** Opens the review of the earlier scan shown in the banner. */
    data object OnEarlierScanReviewClick : NotebookCaptureAction
}
