package com.danzucker.stitchpad.feature.customer.presentation.detail

sealed interface CustomerDetailEvent {
    data object NavigateBack : CustomerDetailEvent
    data class NavigateToEditCustomer(val customerId: String) : CustomerDetailEvent
    data class NavigateToAddMeasurement(val customerId: String) : CustomerDetailEvent
    data class NavigateToViewMeasurement(val customerId: String, val measurementId: String) : CustomerDetailEvent
    data class NavigateToStyleGallery(val customerId: String) : CustomerDetailEvent
    data object NavigateToUpgrade : CustomerDetailEvent

    // PTSP-33: contact the customer from the detail header.
    data class LaunchWhatsApp(val phone: String, val message: String) : CustomerDetailEvent
    data class LaunchDialer(val phone: String) : CustomerDetailEvent
}
