package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

sealed interface WorkshopSetupAction {
    data class OnBusinessNameChange(val name: String) : WorkshopSetupAction
    data class OnWhatsAppNumberChange(val raw: String) : WorkshopSetupAction
    data object OnBusinessNameBlur : WorkshopSetupAction
    data object OnWhatsAppNumberBlur : WorkshopSetupAction
    data object OnContinueClick : WorkshopSetupAction
    data object OnSkipClick : WorkshopSetupAction
    data object OnLogoUploadClick : WorkshopSetupAction
}
