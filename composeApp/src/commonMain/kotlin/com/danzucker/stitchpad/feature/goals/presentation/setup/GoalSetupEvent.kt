package com.danzucker.stitchpad.feature.goals.presentation.setup

sealed interface GoalSetupEvent {
    data object NavigateBack : GoalSetupEvent
    data object GoalSaved : GoalSetupEvent
}
