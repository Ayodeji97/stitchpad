package com.danzucker.stitchpad.feature.settings.presentation.home

sealed interface SettingsAction {
    data object OnBackClick : SettingsAction
    data object OnProfileClick : SettingsAction
    data object OnMeasurementUnitClick : SettingsAction
    data object OnAppearanceClick : SettingsAction
    data object OnReceiptImageStyleClick : SettingsAction
    data object OnEmailRowClick : SettingsAction
    data object OnChangePasswordClick : SettingsAction
    data object OnReferralCodeClick : SettingsAction
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
    data object OnGetGiftedClick : SettingsAction
    data object OnRedeemGiftClick : SettingsAction
    data object OnHelpTutorialsClick : SettingsAction
    data class OnDailyDigestToggle(val enabled: Boolean) : SettingsAction
    data class OnDailyPushToggle(val enabled: Boolean) : SettingsAction
    data object OnCommunityClick : SettingsAction
    data object OnAccountSecurityClick : SettingsAction
    data object OnInviteRewardsClick : SettingsAction
    data object OnHelpSupportClick : SettingsAction
    data object OnLegalAboutClick : SettingsAction

    // Staff-only: leave the owner's workshop (destructive, confirmed via dialog).
    data object OnLeaveWorkshopClick : SettingsAction
    data object OnConfirmLeaveWorkshop : SettingsAction
    data object OnDismissLeaveWorkshopDialog : SettingsAction
}
