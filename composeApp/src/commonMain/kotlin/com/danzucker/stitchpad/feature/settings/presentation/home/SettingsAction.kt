package com.danzucker.stitchpad.feature.settings.presentation.home

sealed interface SettingsAction {
    data object OnProfileClick : SettingsAction
    data object OnMeasurementUnitClick : SettingsAction
    data object OnAppearanceClick : SettingsAction
    data object OnEmailRowClick : SettingsAction
    data object OnChangePasswordClick : SettingsAction
    data object OnSignOutRowClick : SettingsAction
    data object OnSignOutConfirm : SettingsAction
    data object OnSignOutDismiss : SettingsAction
    data object OnPrivacyClick : SettingsAction
    data object OnTermsClick : SettingsAction
    data object OnDeleteAccountClick : SettingsAction
    data object OnInviteClick : SettingsAction
    data object OnContactClick : SettingsAction
    data object OnDebugMenuClick : SettingsAction
    data object OnUpgradeClick : SettingsAction
    data object OnFoundersNoteClick : SettingsAction
    data class OnDailyDigestToggle(val enabled: Boolean) : SettingsAction
}
