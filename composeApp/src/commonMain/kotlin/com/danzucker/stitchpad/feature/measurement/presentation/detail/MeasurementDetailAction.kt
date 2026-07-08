package com.danzucker.stitchpad.feature.measurement.presentation.detail

sealed interface MeasurementDetailAction {
    data object OnEditClick : MeasurementDetailAction
    data object OnRenameClick : MeasurementDetailAction
    data class OnRenameDraftChange(val name: String) : MeasurementDetailAction
    data object OnConfirmRename : MeasurementDetailAction
    data object OnDismissRenameDialog : MeasurementDetailAction
    data object OnDeleteClick : MeasurementDetailAction
    data object OnConfirmDelete : MeasurementDetailAction
    data object OnDismissDeleteDialog : MeasurementDetailAction
    data object OnSavedMessageShown : MeasurementDetailAction
    data object OnNavigateBack : MeasurementDetailAction
    data object OnErrorDismiss : MeasurementDetailAction
}
