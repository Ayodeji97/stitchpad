package com.danzucker.stitchpad.feature.tutorials.presentation.hint

import com.danzucker.stitchpad.feature.tutorials.domain.model.Tutorial

data class TutorialHintUiState(
    val tutorial: Tutorial? = null,
    val expanded: Boolean = false,
    /** False until the catalog + seen-flag have been read once, so nothing flashes on entry. */
    val resolved: Boolean = false,
)

sealed interface TutorialHintAction {
    data object OnWatch : TutorialHintAction
    data object OnDismiss : TutorialHintAction
}

sealed interface TutorialHintEvent {
    data class NavigateToPlayer(val tutorialId: String) : TutorialHintEvent
}
