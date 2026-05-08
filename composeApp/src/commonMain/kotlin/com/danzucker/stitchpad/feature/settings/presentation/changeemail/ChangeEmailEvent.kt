package com.danzucker.stitchpad.feature.settings.presentation.changeemail

import com.danzucker.stitchpad.core.presentation.UiText

sealed interface ChangeEmailEvent {
    data object NavigateBack : ChangeEmailEvent
    data class ShowSnackbar(val message: UiText) : ChangeEmailEvent
    data class SaveSucceeded(val message: UiText) : ChangeEmailEvent
}
