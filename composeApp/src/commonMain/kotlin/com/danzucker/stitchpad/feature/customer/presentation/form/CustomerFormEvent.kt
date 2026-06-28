package com.danzucker.stitchpad.feature.customer.presentation.form

sealed interface CustomerFormEvent {
    data object NavigateBack : CustomerFormEvent

    /**
     * Emitted after a successful create (add mode, "Add measurements next"
     * unchecked) so the Root lands on the Customers list rather than popping
     * back to wherever the form was opened from (e.g. the dashboard FAB).
     */
    data object NavigateToCustomerList : CustomerFormEvent

    /**
     * Emitted when createCustomer fails with CAP_REACHED so the screen can
     * show the cap-reached ModalBottomSheet instead of a generic snackbar.
     * Carries the current active count and the cap so the sheet can render
     * "X of Y active customers" without re-fetching from Firestore.
     */
    data class ShowCapReachedSheet(
        val activeCount: Int,
        val customerCap: Int,
    ) : CustomerFormEvent

    /**
     * Emitted on a successful add-mode save when the user chose
     * "Add measurements next". Carries the newly-minted customer id so the
     * Root can chain navigate(CustomerDetail) + navigate(MeasurementForm).
     */
    data class NavigateToNewCustomerMeasurement(
        val customerId: String,
    ) : CustomerFormEvent
}
