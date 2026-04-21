package com.danzucker.stitchpad.feature.order.presentation.detail

import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.presentation.UiText

data class OrderDetailState(
    val order: Order? = null,
    val isLoading: Boolean = true,
    val showDeleteDialog: Boolean = false,
    val showStatusUpdateDialog: Boolean = false,
    val selectedNewStatus: OrderStatus? = null,
    val errorMessage: UiText? = null
)
