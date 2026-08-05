package com.danzucker.stitchpad.feature.foundingtailors.presentation

import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.referral.domain.FoundingTailorsStanding

data class FoundingTailorsState(
    val isLoading: Boolean = false,
    val referralUrl: String? = null,
    val isStandingLoading: Boolean = false,
    val standing: FoundingTailorsStanding? = null,
    val error: UiText? = null,
)

sealed interface FoundingTailorsAction {
    data object LoadLink : FoundingTailorsAction
    data object ShareLink : FoundingTailorsAction
    data object OpenLeaderboard : FoundingTailorsAction
}

sealed interface FoundingTailorsEvent {
    data class ShareText(val text: String) : FoundingTailorsEvent
    data class OpenUrl(val url: String) : FoundingTailorsEvent
}
