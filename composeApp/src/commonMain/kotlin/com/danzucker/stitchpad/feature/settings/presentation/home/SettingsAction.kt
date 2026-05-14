package com.danzucker.stitchpad.feature.settings.presentation.home

import com.danzucker.stitchpad.core.domain.preferences.ThemePreference

sealed interface SettingsAction {
    data object OnProfileClick : SettingsAction
    data object OnUpgradeClick : SettingsAction
    data object OnComparePlansClick : SettingsAction
    data object OnMeasurementUnitClick : SettingsAction
    data class OnThemeSelect(val theme: ThemePreference) : SettingsAction
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
}
