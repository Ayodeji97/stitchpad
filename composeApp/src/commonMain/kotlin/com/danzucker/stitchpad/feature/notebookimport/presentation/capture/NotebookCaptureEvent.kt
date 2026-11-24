package com.danzucker.stitchpad.feature.notebookimport.presentation.capture

sealed interface NotebookCaptureEvent {
    data object NavigateBack : NotebookCaptureEvent
    data class NavigateToReview(val jobId: String) : NotebookCaptureEvent
}
