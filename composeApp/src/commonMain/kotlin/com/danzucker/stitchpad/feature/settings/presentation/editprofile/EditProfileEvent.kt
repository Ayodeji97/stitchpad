package com.danzucker.stitchpad.feature.settings.presentation.editprofile

import com.danzucker.stitchpad.core.presentation.UiText

sealed interface EditProfileEvent {
    data object NavigateBack : EditProfileEvent
    data class ShowSnackbar(val message: UiText) : EditProfileEvent
}
