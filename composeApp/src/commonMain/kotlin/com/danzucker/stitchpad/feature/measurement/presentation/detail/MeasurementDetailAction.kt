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
    data object OnShareClick : MeasurementDetailAction
    data object OnShareAsImageClick : MeasurementDetailAction
    data object OnShareAsPdfClick : MeasurementDetailAction
    data object OnShareWhatsAppClick : MeasurementDetailAction
    data object OnDismissShareSheet : MeasurementDetailAction

    /** Empty-state hero CTA. */
    data object OnAddMeasurementClick : MeasurementDetailAction
}
