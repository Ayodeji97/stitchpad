package com.danzucker.stitchpad.feature.customer.presentation.form

sealed interface CustomerFormEvent {
    data object NavigateBack : CustomerFormEvent
}
