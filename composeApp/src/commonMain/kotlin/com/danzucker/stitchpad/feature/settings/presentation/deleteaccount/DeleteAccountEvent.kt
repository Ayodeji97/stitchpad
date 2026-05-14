package com.danzucker.stitchpad.feature.settings.presentation.deleteaccount

import com.danzucker.stitchpad.core.presentation.UiText

sealed interface DeleteAccountEvent {
    /** Cancel paths: returns to Settings without changing anything. */
    data object NavigateBack : DeleteAccountEvent

    /** Successful delete: clears the back stack and routes to Login. */
    data object NavigateToLoginAfterDelete : DeleteAccountEvent

    data class ShowSnackbar(val message: UiText) : DeleteAccountEvent
}
