package com.danzucker.stitchpad.feature.notebookimport.presentation.card

sealed interface NotebookCardEvent {
    data object NavigateBack : NotebookCardEvent
}
