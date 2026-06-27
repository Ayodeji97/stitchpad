package com.danzucker.stitchpad.feature.tutorials.presentation.player

sealed interface TutorialPlayerEvent {
    data object NavigateBack : TutorialPlayerEvent
}
