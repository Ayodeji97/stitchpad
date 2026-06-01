package com.danzucker.stitchpad.feature.settings.presentation.editprofile

sealed interface EditProfileAction {
    data class OnBusinessNameChange(val value: String) : EditProfileAction
    data class OnDisplayNameChange(val value: String) : EditProfileAction
    data class OnPhoneChange(val value: String) : EditProfileAction
    data class OnWhatsappChange(val value: String) : EditProfileAction
    data class OnAvatarColorSelect(val index: Int) : EditProfileAction
    data object OnBusinessNameBlur : EditProfileAction
    data object OnPhoneBlur : EditProfileAction
    data object OnWhatsappBlur : EditProfileAction
    data object OnSaveClick : EditProfileAction
    data object OnBackClick : EditProfileAction

    // Logo
    data class OnLogoPicked(val bytes: ByteArray) : EditProfileAction
    data object OnLogoRemoveClick : EditProfileAction
    data object OnLogoRemoveConfirm : EditProfileAction
    data object OnLogoRemoveDismiss : EditProfileAction

    // Bank details (PTSP-16)
    data class OnBankNameChange(val value: String) : EditProfileAction
    data class OnBankAccountNameChange(val value: String) : EditProfileAction
    data class OnBankAccountNumberChange(val value: String) : EditProfileAction
    data object OnBankNameBlur : EditProfileAction
    data object OnBankAccountNameBlur : EditProfileAction
    data object OnBankAccountNumberBlur : EditProfileAction

    // WhatsApp confirm
    data object OnConfirmWhatsAppClick : EditProfileAction
    data class OnConfirmCodeChange(val value: String) : EditProfileAction
    data object OnDismissConfirm : EditProfileAction
}
