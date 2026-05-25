package com.danzucker.stitchpad.feature.customer.presentation.form

sealed interface CustomerFormEvent {
    data object NavigateBack : CustomerFormEvent

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
}
