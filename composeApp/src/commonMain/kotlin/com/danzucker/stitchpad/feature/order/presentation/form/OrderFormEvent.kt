package com.danzucker.stitchpad.feature.order.presentation.form

sealed interface OrderFormEvent {
    data object NavigateBack : OrderFormEvent

    /** Edit-mode save — pop back to wherever the form was opened (order detail). */
    data object OrderSaved : OrderFormEvent

    /**
     * Fresh create — land on the Orders list so the tailor sees the order they
     * just added, rather than popping back to the dashboard FAB.
     */
    data object OrderCreated : OrderFormEvent
    data class ShowCustomSavedSnackbar(val name: String) : OrderFormEvent
}
