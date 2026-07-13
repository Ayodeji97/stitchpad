package com.danzucker.stitchpad.feature.debug.presentation

sealed interface DebugMenuAction {
    data object OnBackClick : DebugMenuAction
    data object OnSeedBrandNewClick : DebugMenuAction
    data object OnSeedActiveWorkshopClick : DebugMenuAction
    data object OnSeedAllReconnectClick : DebugMenuAction
    data object OnBulkSeedClick : DebugMenuAction
    data object OnBulkSeedDismiss : DebugMenuAction
    data class OnBulkSeedTotalChange(val value: String) : DebugMenuAction
    data class OnBulkSeedMeasurementsChange(val value: String) : DebugMenuAction
    data class OnBulkSeedOrdersChange(val value: String) : DebugMenuAction
    data object OnBulkSeedConfirm : DebugMenuAction
    data object OnClearActiveScenarioClick : DebugMenuAction
    data object OnResetOnboardingClick : DebugMenuAction
    data object OnSignOutClick : DebugMenuAction
    data object OnSwitchToFolaClick : DebugMenuAction
    data object OnSwitchToGabbyClick : DebugMenuAction
    data object OnWipeDataClick : DebugMenuAction

    data object OnSetTierFreeClick : DebugMenuAction
    data object OnSetTierProClick : DebugMenuAction
    data object OnSetTierAtelierClick : DebugMenuAction
    data object OnExpireWelcomeWindowClick : DebugMenuAction
    data object OnResetWelcomeWindowClick : DebugMenuAction
    data object OnSetWelcomeDaysLeftClick : DebugMenuAction
    data object OnSetWelcomeDaysLeftDismiss : DebugMenuAction
    data class OnSetWelcomeDaysLeftChange(val value: String) : DebugMenuAction
    data object OnSetWelcomeDaysLeftConfirm : DebugMenuAction
    data object OnDrainBonusCoinsClick : DebugMenuAction
    data object OnRefillBonusCoinsClick : DebugMenuAction
    data object OnResetSmartUsageClick : DebugMenuAction
    data object OnSetSmartUsageClick : DebugMenuAction
    data object OnSetSmartUsageDismiss : DebugMenuAction
    data class OnSetSmartUsageCountChange(val value: String) : DebugMenuAction
    data class OnSetSmartUsageBonusUsedChange(val value: String) : DebugMenuAction
    data object OnSetSmartUsageConfirm : DebugMenuAction
    data object OnReconcileSlotsClick : DebugMenuAction

    data object OnResetCommunityBannerClick : DebugMenuAction
    data object OnResetCelebrationsClick : DebugMenuAction

    // Referral (client)
    data object OnReferralAttributeClick : DebugMenuAction
    data object OnReferralAttributeDismiss : DebugMenuAction
    data class OnReferralAttributeCodeChange(val value: String) : DebugMenuAction
    data object OnReferralAttributeConfirm : DebugMenuAction
    data object OnReferralSeedQualificationClick : DebugMenuAction
    data object OnReferralResetCaptureClick : DebugMenuAction

    // Referral (admin only — deployed debug callables)
    data object OnReferralRunGraderClick : DebugMenuAction
    data object OnReferralRunConfirmClick : DebugMenuAction
    data object OnReferralRunSweepClick : DebugMenuAction

    data object OnSendDailyDigestClick : DebugMenuAction
    data object OnSendTestPushClick : DebugMenuAction
    data object OnSendRenewalReminderClick : DebugMenuAction

    data class ToggleAnalyticsCollection(val enabled: Boolean) : DebugMenuAction
}
