package com.danzucker.stitchpad.feature.gift.presentation.sharelink

import com.danzucker.stitchpad.core.presentation.UiText

sealed interface ShareGiftLinkEvent {
    data class CopyToClipboard(val text: String) : ShareGiftLinkEvent
    data class ShareViaWhatsApp(val url: String) : ShareGiftLinkEvent
    data class ShowSnackbar(val message: UiText) : ShareGiftLinkEvent
    data object NavigateBack : ShareGiftLinkEvent
}
