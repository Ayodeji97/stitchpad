package com.danzucker.stitchpad.feature.notification.presentation.inbox

sealed interface NotificationsInboxEvent {
    data object NavigateBack : NotificationsInboxEvent
    data class NavigateToOrderDetail(val orderId: String) : NotificationsInboxEvent
}
