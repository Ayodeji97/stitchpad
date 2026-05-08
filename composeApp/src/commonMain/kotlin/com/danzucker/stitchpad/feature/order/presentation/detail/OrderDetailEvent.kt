package com.danzucker.stitchpad.feature.order.presentation.detail

sealed interface OrderDetailEvent {
    data class NavigateToOrderForm(val orderId: String) : OrderDetailEvent
    data class NavigateToCustomerDetail(val customerId: String) : OrderDetailEvent
    data class NavigateToCustomerForm(val customerId: String) : OrderDetailEvent
    data class NavigateToCreateOrder(val seedFromOrderId: String) : OrderDetailEvent
    data class NavigateToMeasurementForm(val customerId: String, val linkToOrderId: String) : OrderDetailEvent
    data class NavigateToStyleForm(val customerId: String, val linkToOrderId: String) : OrderDetailEvent
    data class LaunchWhatsApp(val phone: String, val message: String) : OrderDetailEvent
    data class LaunchDialer(val phone: String) : OrderDetailEvent
    data object NavigateBack : OrderDetailEvent
    data object OrderDeleted : OrderDetailEvent
    data object OrderArchived : OrderDetailEvent
    data object PaymentRecorded : OrderDetailEvent
    data object NotesSaved : OrderDetailEvent
}
