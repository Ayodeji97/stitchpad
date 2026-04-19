package com.danzucker.stitchpad.feature.style.presentation.form

sealed interface StyleFormEvent {
    data object NavigateBack : StyleFormEvent
}
