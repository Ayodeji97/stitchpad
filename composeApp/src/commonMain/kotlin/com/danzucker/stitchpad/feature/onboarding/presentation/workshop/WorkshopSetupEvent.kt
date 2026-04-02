package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

import com.danzucker.stitchpad.core.presentation.UiText

sealed interface WorkshopSetupEvent {
    data object NavigateToHome : WorkshopSetupEvent
    data class ShowError(val message: UiText) : WorkshopSetupEvent
}
