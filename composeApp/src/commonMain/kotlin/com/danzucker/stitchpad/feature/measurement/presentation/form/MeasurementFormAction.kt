package com.danzucker.stitchpad.feature.measurement.presentation.form

import com.danzucker.stitchpad.core.domain.model.CustomerGender

sealed interface MeasurementFormAction {
    data class OnGenderChange(val gender: CustomerGender) : MeasurementFormAction
    data class OnSectionChange(val index: Int) : MeasurementFormAction
    data object OnNextSection : MeasurementFormAction
    data object OnPreviousSection : MeasurementFormAction
    data object OnToggleShowMore : MeasurementFormAction
    data object OnToggleNotes : MeasurementFormAction
    data class OnFieldChange(val key: String, val value: String) : MeasurementFormAction
    data class OnNotesChange(val notes: String) : MeasurementFormAction
    data object OnSaveClick : MeasurementFormAction
    data object OnNavigateBack : MeasurementFormAction
    data object OnErrorDismiss : MeasurementFormAction
}
