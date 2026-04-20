package com.danzucker.stitchpad.feature.order.presentation.list

import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus

sealed interface OrderListAction {
    data class OnStatusFilterChange(val status: OrderStatus?) : OrderListAction
    data class OnOrderClick(val order: Order) : OrderListAction
    data class OnDeleteOrderClick(val order: Order) : OrderListAction
    data object OnAddOrderClick : OrderListAction
    data object OnConfirmDelete : OrderListAction
    data object OnDismissDeleteDialog : OrderListAction
    data object OnErrorDismiss : OrderListAction
}
