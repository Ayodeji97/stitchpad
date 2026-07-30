package com.danzucker.stitchpad.feature.staff.presentation.team

import com.danzucker.stitchpad.core.presentation.UiText

sealed interface TeamEvent {
    data object NavigateBack : TeamEvent
    data class CopyToClipboard(val text: String) : TeamEvent
    data class ShareViaWhatsApp(val url: String) : TeamEvent
    data class ShowSnackbar(val text: UiText) : TeamEvent
}
