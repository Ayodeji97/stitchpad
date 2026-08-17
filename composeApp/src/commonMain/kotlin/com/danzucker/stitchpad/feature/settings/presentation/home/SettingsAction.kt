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

    /** Slice 7: owner opens the Team management screen. */
    data object OnTeamClick : SettingsAction

    /** Standalone/demoted user opens the invite-code redeem screen (code shared as text, no deep link). */
    data object OnJoinWorkshopClick : SettingsAction
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

    /** Invite & rewards: open the Founding Tailors referral + leaderboard screen. */
    data object OnFoundingTailorsClick : SettingsAction
    data object OnGetGiftedClick : SettingsAction
    data object OnRedeemGiftClick : SettingsAction
    data object OnHelpTutorialsClick : SettingsAction
    data class OnDailyDigestToggle(val enabled: Boolean) : SettingsAction
    data class OnDailyPushToggle(val enabled: Boolean) : SettingsAction
    data class OnAnnouncementsPushToggle(val enabled: Boolean) : SettingsAction
    data object OnCommunityClick : SettingsAction
    data object OnAccountSecurityClick : SettingsAction
    data object OnInviteRewardsClick : SettingsAction
    data object OnHelpSupportClick : SettingsAction
    data object OnLegalAboutClick : SettingsAction
    data object OnRateAppClick : SettingsAction

    // Staff-only: leave the owner's workshop (destructive, confirmed via dialog).
    data object OnLeaveWorkshopClick : SettingsAction
    data object OnConfirmLeaveWorkshop : SettingsAction
    data object OnDismissLeaveWorkshopDialog : SettingsAction
}
