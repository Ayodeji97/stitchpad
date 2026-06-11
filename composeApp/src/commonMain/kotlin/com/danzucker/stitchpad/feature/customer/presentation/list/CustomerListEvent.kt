package com.danzucker.stitchpad.feature.customer.presentation.list

sealed interface CustomerListEvent {
    data object NavigateToAddCustomer : CustomerListEvent
    data class NavigateToCustomerDetail(val customerId: String) : CustomerListEvent
    data class SwapSucceeded(val promotedFirstName: String) : CustomerListEvent
    data object SwapFailed : CustomerListEvent
    data class NavigateToEditCustomer(val customerId: String) : CustomerListEvent
    data class NavigateToAddMeasurement(val customerId: String) : CustomerListEvent
    data class NavigateToOrderForm(val customerId: String) : CustomerListEvent

    /** PTSP-32: open WhatsApp to the customer with a prefilled greeting. */
    data class LaunchWhatsApp(val phone: String, val message: String) : CustomerListEvent
}
