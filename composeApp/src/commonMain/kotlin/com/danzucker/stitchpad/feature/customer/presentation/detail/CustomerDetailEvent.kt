package com.danzucker.stitchpad.feature.customer.presentation.detail

sealed interface CustomerDetailEvent {
    data object NavigateBack : CustomerDetailEvent
    data class NavigateToEditCustomer(val customerId: String) : CustomerDetailEvent
    data class NavigateToAddMeasurement(val customerId: String) : CustomerDetailEvent
    data class NavigateToEditMeasurement(val customerId: String, val measurementId: String) : CustomerDetailEvent
    data class NavigateToStyleGallery(val customerId: String) : CustomerDetailEvent
}
