package com.danzucker.stitchpad.feature.debug.presentation

import com.danzucker.stitchpad.core.presentation.UiText

sealed interface DebugMenuEvent {
    data object NavigateBack : DebugMenuEvent
    data object NavigateToLogin : DebugMenuEvent
    data object NavigateToSplash : DebugMenuEvent
    data class ShowSnackbar(val message: UiText) : DebugMenuEvent
}
