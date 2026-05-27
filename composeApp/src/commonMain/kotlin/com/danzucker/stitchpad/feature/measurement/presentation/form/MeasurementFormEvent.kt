package com.danzucker.stitchpad.feature.measurement.presentation.form

sealed interface MeasurementFormEvent {
    data object NavigateBack : MeasurementFormEvent

    /**
     * PTSP-12 — emitted when a non-entitled tailor taps the locked
     * "+ Add custom field" affordance. The Root translates this into a
     * navigation to the existing UpgradeRoute (no new conversion UI).
     */
    data object NavigateToUpgrade : MeasurementFormEvent
}
