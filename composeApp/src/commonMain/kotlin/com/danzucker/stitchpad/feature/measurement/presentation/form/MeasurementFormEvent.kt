package com.danzucker.stitchpad.feature.measurement.presentation.form

sealed interface MeasurementFormEvent {
    data object NavigateBack : MeasurementFormEvent
}
