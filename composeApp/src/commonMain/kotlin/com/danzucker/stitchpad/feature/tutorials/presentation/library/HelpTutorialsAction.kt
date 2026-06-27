package com.danzucker.stitchpad.feature.tutorials.presentation.library

sealed interface HelpTutorialsAction {
    data object OnBack : HelpTutorialsAction
    data class OnTutorialClick(val tutorialId: String) : HelpTutorialsAction
}
