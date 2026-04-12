package com.danzucker.stitchpad.feature.customer.presentation.detail

sealed interface CustomerDetailEvent {
    data object NavigateBack : CustomerDetailEvent
    data object NavigateToEditCustomer : CustomerDetailEvent
    data object NavigateToAddMeasurement : CustomerDetailEvent
    data class NavigateToEditMeasurement(val measurementId: String) : CustomerDetailEvent
}
