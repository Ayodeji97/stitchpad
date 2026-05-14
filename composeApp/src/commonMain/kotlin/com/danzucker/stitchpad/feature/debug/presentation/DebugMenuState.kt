package com.danzucker.stitchpad.feature.debug.presentation

enum class DebugScenario {
    BrandNew,
    ActiveWorkshop,
    AllReconnect,
}

data class DebugMenuState(
    val isWorking: Boolean = false,
    val testAccountsConfigured: Boolean = false,
    val activeScenario: DebugScenario? = null,
)
