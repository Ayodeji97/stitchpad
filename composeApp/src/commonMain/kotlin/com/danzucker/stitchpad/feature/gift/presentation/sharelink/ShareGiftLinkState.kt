package com.danzucker.stitchpad.feature.gift.presentation.sharelink

import com.danzucker.stitchpad.feature.gift.domain.GiftLink

data class ShareGiftLinkState(
    val isLoading: Boolean = true,
    val link: GiftLink? = null,
    val hasError: Boolean = false,
)
