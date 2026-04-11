package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

sealed interface WorkshopSetupAction {
    data class OnBusinessNameChange(val name: String) : WorkshopSetupAction
    data class OnPhoneChange(val phone: String) : WorkshopSetupAction
    data object OnBusinessNameBlur : WorkshopSetupAction
    data object OnPhoneBlur : WorkshopSetupAction
    data object OnContinueClick : WorkshopSetupAction
    data object OnSkipClick : WorkshopSetupAction
}
