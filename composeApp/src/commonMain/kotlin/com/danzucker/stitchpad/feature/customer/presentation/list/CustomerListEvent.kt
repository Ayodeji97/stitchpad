package com.danzucker.stitchpad.feature.customer.presentation.list

sealed interface CustomerListEvent {
    data object NavigateToAddCustomer : CustomerListEvent
    data class NavigateToCustomerDetail(val customerId: String) : CustomerListEvent
    data class SwapSucceeded(val promotedFirstName: String) : CustomerListEvent
    data object SwapFailed : CustomerListEvent
    data class NavigateToEditCustomer(val customerId: String) : CustomerListEvent
    data class NavigateToAddMeasurement(val customerId: String) : CustomerListEvent
    data class NavigateToOrderForm(val customerId: String) : CustomerListEvent
}
