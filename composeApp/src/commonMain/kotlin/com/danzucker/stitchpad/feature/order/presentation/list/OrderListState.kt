package com.danzucker.stitchpad.feature.order.presentation.list

import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.presentation.UiText

data class OrderListState(
    val orders: List<Order> = emptyList(),
    val statusFilter: OrderStatus? = null,
    /** When true, [orders] holds archived orders (Restore affordance) instead of the active list. */
    val showArchived: Boolean = false,
    val isLoading: Boolean = true,
    val showDeleteDialog: Boolean = false,
    val orderToDelete: Order? = null,
    val errorMessage: UiText? = null
)
