package com.danzucker.stitchpad.feature.order.presentation.detail

import com.danzucker.stitchpad.core.domain.model.OrderStatus

sealed interface OrderDetailAction {
    data object OnEditClick : OrderDetailAction
    data object OnDeleteClick : OrderDetailAction
    data object OnConfirmDelete : OrderDetailAction
    data object OnDismissDeleteDialog : OrderDetailAction
    data object OnUpdateStatusClick : OrderDetailAction
    data class OnSelectNewStatus(val status: OrderStatus) : OrderDetailAction
    data object OnConfirmStatusUpdate : OrderDetailAction
    data object OnDismissStatusUpdate : OrderDetailAction
    data object OnCustomerClick : OrderDetailAction
    data object OnShareClick : OrderDetailAction
    data object OnBackClick : OrderDetailAction
    data object OnErrorDismiss : OrderDetailAction
}
