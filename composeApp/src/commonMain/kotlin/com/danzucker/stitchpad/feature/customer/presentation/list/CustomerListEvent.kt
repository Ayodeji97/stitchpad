package com.danzucker.stitchpad.feature.customer.presentation.list

sealed interface CustomerListEvent {
    data object NavigateToAddCustomer : CustomerListEvent
    data class NavigateToEditCustomer(val customerId: String) : CustomerListEvent
}
