package com.danzucker.stitchpad.feature.measurement.presentation.form

sealed interface MeasurementFormEvent {
    data object NavigateBack : MeasurementFormEvent

    /**
     * Emitted when a tailor taps "Skip for now" on the measurement form during
     * customer creation. The customer is already saved by this point, so the
     * Root navigates back (to the new customer's detail) WITHOUT writing a
     * measurement. Same destination as a successful save.
     */
    data object SkipMeasurements : MeasurementFormEvent

    /**
     * PTSP-12 — emitted when a non-entitled tailor taps the locked
     * "+ Add custom field" affordance. The Root translates this into a
     * navigation to the existing UpgradeRoute (no new conversion UI).
     */
    data object NavigateToUpgrade : MeasurementFormEvent

    /**
     * Save succeeded on a standalone create/edit — land on the read-only detail
     * view (replacing the form in the back stack). The chained flows — customer
     * creation and order-linking — keep [NavigateBack] so they return to their
     * parent (customer detail / order detail).
     */
    data class MeasurementSaved(val customerId: String, val measurementId: String) : MeasurementFormEvent
}
