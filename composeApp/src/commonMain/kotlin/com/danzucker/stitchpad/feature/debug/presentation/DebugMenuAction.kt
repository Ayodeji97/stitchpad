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
    data class OnSetSmartUsageBonusChange(val value: String) : DebugMenuAction
    data object OnSetSmartUsageConfirm : DebugMenuAction
    data object OnReconcileSlotsClick : DebugMenuAction
}
