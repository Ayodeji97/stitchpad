package com.danzucker.stitchpad.feature.settings.presentation.editprofile

import com.danzucker.stitchpad.core.presentation.UiText

sealed interface EditProfileEvent {
    data object NavigateBack : EditProfileEvent

    /**
     * Snackbar that fires-and-stays — for errors and other non-terminal feedback
     * where the user remains on the screen.
     */
    data class ShowSnackbar(val message: UiText) : EditProfileEvent

    /**
     * Single event for the success path: the Root suspends on the snackbar
     * before calling onNavigateBack so the user actually sees the confirmation.
     * Splitting into ShowSnackbar + NavigateBack races the snackbar against
     * the scaffold teardown.
     */
    data class SaveSucceeded(val message: UiText) : EditProfileEvent

    data class LaunchWhatsAppConfirm(val phoneE164: String, val code: String) : EditProfileEvent
}
