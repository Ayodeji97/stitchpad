package com.danzucker.stitchpad.feature.debug.presentation

import com.danzucker.stitchpad.core.presentation.UiText

data class DebugMenuState(
    val isWorking: Boolean = false,
    val lastResult: UiText? = null,
    val testAccountsConfigured: Boolean = false,
)
