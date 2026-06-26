package com.danzucker.stitchpad.feature.tutorials.presentation.player

sealed interface TutorialPlayerAction {
    data object OnClose : TutorialPlayerAction
    data object OnRetry : TutorialPlayerAction
}
