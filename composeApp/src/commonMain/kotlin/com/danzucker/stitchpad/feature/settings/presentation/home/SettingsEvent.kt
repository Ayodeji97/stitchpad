package com.danzucker.stitchpad.feature.settings.presentation.home

import com.danzucker.stitchpad.core.presentation.UiText
import org.jetbrains.compose.resources.StringResource

sealed interface SettingsEvent {
    data object NavigateToEditProfile : SettingsEvent
    data object NavigateToChangeEmail : SettingsEvent
    data object NavigateToChangePassword : SettingsEvent
    data object NavigateToDeleteAccount : SettingsEvent
    data object NavigateToLoginAfterSignOut : SettingsEvent
    data object NavigateToDebugMenu : SettingsEvent
    data class OpenUrl(val url: String) : SettingsEvent

    /**
     * Open WhatsApp with a localizable message body. The Root resolves
     * [messageRes] against compose.resources, then constructs the wa.me
     * URL — keeps user-facing copy in strings.xml without forcing the
     * VM to resolve resources.
     *
     * Empty [phoneNumber] opens WhatsApp's universal share picker (no
     * direct chat target).
     */
    data class OpenWhatsApp(val phoneNumber: String, val messageRes: StringResource) : SettingsEvent
    data class ShowSnackbar(val message: UiText) : SettingsEvent
    data object NavigateToUpgrade : SettingsEvent
    data object NavigateToFoundersNote : SettingsEvent
    data object NavigateToShareGiftLink : SettingsEvent
}
