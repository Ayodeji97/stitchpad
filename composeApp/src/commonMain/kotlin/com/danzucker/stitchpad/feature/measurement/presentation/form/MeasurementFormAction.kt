package com.danzucker.stitchpad.feature.measurement.presentation.form

import com.danzucker.stitchpad.core.domain.model.GarmentType

sealed interface MeasurementFormAction {
    data class OnGarmentTypeChange(val type: GarmentType) : MeasurementFormAction
    data class OnFieldChange(val label: String, val value: String) : MeasurementFormAction
    data class OnNotesChange(val notes: String) : MeasurementFormAction
    data object OnSaveClick : MeasurementFormAction
    data object OnNavigateBack : MeasurementFormAction
    data object OnErrorDismiss : MeasurementFormAction
}
