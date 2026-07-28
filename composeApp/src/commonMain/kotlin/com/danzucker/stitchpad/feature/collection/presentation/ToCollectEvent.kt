package com.danzucker.stitchpad.feature.collection.presentation

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.Order

sealed interface ToCollectEvent {
    data object NavigateBack : ToCollectEvent
    data class NavigateToOrderDetail(val orderId: String) : ToCollectEvent
    data class LaunchWhatsApp(val order: Order, val customer: Customer) : ToCollectEvent
}
