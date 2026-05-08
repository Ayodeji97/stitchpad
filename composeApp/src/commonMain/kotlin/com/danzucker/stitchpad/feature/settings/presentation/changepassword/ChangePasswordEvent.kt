package com.danzucker.stitchpad.feature.settings.presentation.changepassword

import com.danzucker.stitchpad.core.presentation.UiText

sealed interface ChangePasswordEvent {
    data object NavigateBack : ChangePasswordEvent
    data class ShowSnackbar(val message: UiText) : ChangePasswordEvent
    data class SaveSucceeded(val message: UiText) : ChangePasswordEvent
}
