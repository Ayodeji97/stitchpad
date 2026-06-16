package com.danzucker.stitchpad.feature.gift.presentation.sharelink

sealed interface ShareGiftLinkAction {
    data object OnCopyClick : ShareGiftLinkAction
    data object OnShareClick : ShareGiftLinkAction
    data object OnRetry : ShareGiftLinkAction
    data object OnBack : ShareGiftLinkAction
}
