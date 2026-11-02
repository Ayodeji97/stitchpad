package com.danzucker.stitchpad.feature.goals.presentation.setup

import com.danzucker.stitchpad.core.domain.model.Currency
import com.danzucker.stitchpad.core.presentation.UiText

data class GoalSetupState(
    val targetAmountInput: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: UiText? = null,
    val currency: Currency = Currency.NGN,
    val isCurrencyLoaded: Boolean = false,
) {
    /** Save button enables once the user has typed at least one digit. */
    val canSave: Boolean
        get() = !isSaving && isCurrencyLoaded && targetAmountInput.isNotBlank()
}
