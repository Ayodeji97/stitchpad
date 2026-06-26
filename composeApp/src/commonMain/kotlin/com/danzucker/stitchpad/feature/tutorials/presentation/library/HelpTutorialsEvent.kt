package com.danzucker.stitchpad.feature.tutorials.presentation.library

sealed interface HelpTutorialsEvent {
    data object NavigateBack : HelpTutorialsEvent
    data class NavigateToPlayer(val tutorialId: String) : HelpTutorialsEvent
}
