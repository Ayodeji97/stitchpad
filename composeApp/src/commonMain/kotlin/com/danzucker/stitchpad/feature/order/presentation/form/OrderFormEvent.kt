package com.danzucker.stitchpad.feature.order.presentation.form

sealed interface OrderFormEvent {
    data object NavigateBack : OrderFormEvent
    data object OrderSaved : OrderFormEvent
}
