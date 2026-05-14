package com.danzucker.stitchpad.feature.debug.presentation

sealed interface DebugMenuAction {
    data object OnBackClick : DebugMenuAction
    data object OnSeedBrandNewClick : DebugMenuAction
    data object OnSeedActiveWorkshopClick : DebugMenuAction
    data object OnSeedAllReconnectClick : DebugMenuAction
    data object OnResetOnboardingClick : DebugMenuAction
    data object OnSignOutClick : DebugMenuAction
    data object OnSwitchToFolaClick : DebugMenuAction
    data object OnSwitchToGabbyClick : DebugMenuAction
    data object OnDeleteAllDataClick : DebugMenuAction
}
