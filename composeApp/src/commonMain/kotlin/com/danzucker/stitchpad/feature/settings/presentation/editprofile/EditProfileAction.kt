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
}
