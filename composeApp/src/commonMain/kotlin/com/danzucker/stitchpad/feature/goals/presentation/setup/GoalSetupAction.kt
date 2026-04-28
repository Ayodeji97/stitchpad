package com.danzucker.stitchpad.feature.goals.presentation.setup

sealed interface GoalSetupAction {
    data class OnTargetAmountChange(val value: String) : GoalSetupAction
    data class OnQuickPickClick(val amount: Long) : GoalSetupAction
    data object OnSaveClick : GoalSetupAction
    data object OnBackClick : GoalSetupAction
    data object OnErrorDismiss : GoalSetupAction
}
