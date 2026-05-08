package com.danzucker.stitchpad.feature.settings.presentation.home

import com.danzucker.stitchpad.core.presentation.UiText

sealed interface SettingsEvent {
    data object NavigateToEditProfile : SettingsEvent
    data object NavigateToChangeEmail : SettingsEvent
    data object NavigateToChangePassword : SettingsEvent
    data object NavigateToDeleteAccount : SettingsEvent
    data object NavigateToLoginAfterSignOut : SettingsEvent
    data class OpenUrl(val url: String) : SettingsEvent
    data class ShowSnackbar(val message: UiText) : SettingsEvent
}
