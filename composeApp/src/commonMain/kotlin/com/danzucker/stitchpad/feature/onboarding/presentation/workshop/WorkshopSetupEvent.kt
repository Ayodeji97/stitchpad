package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

import com.danzucker.stitchpad.core.presentation.UiText

sealed interface WorkshopSetupEvent {
    data object NavigateToHome : WorkshopSetupEvent
    data object NavigateToLogin : WorkshopSetupEvent

    /** Slice 7: user chose the "joining as staff" fork — go to the redeem screen. */
    data object NavigateToJoinWorkshop : WorkshopSetupEvent
    data class ShowError(val message: UiText) : WorkshopSetupEvent
    data class ShowSnackbar(val message: UiText) : WorkshopSetupEvent
    data class LaunchWhatsAppConfirm(val phoneE164: String, val code: String) : WorkshopSetupEvent
}
