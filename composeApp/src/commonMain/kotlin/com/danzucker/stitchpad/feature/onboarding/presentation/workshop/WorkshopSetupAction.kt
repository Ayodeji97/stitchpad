package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

sealed interface WorkshopSetupAction {
    data class OnBusinessNameChange(val name: String) : WorkshopSetupAction
    data class OnWhatsAppNumberChange(val raw: String) : WorkshopSetupAction
    data object OnBusinessNameBlur : WorkshopSetupAction
    data object OnWhatsAppNumberBlur : WorkshopSetupAction
    data object OnContinueClick : WorkshopSetupAction
    data object OnSkipClick : WorkshopSetupAction
    data class OnLogoPicked(val bytes: ByteArray) : WorkshopSetupAction
    data object OnLogoRetry : WorkshopSetupAction

    // WhatsApp confirmation flow
    data object OnConfirmWhatsAppClick : WorkshopSetupAction
    data class OnConfirmCodeChange(val value: String) : WorkshopSetupAction
    data object OnDismissConfirm : WorkshopSetupAction

    // Payment details (PTSP-16)
    data object OnTogglePaymentDetails : WorkshopSetupAction
    data class OnBankNameChange(val value: String) : WorkshopSetupAction
    data class OnBankAccountNameChange(val value: String) : WorkshopSetupAction
    data class OnBankAccountNumberChange(val value: String) : WorkshopSetupAction
    data object OnBankNameBlur : WorkshopSetupAction
    data object OnBankAccountNameBlur : WorkshopSetupAction
    data object OnBankAccountNumberBlur : WorkshopSetupAction
}
