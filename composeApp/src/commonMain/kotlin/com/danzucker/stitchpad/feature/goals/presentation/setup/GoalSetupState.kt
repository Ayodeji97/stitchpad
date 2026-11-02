package com.danzucker.stitchpad.feature.goals.presentation.setup

import com.danzucker.stitchpad.core.domain.model.Currency
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.core.presentation.moneyInputValue

data class GoalSetupState(
    val targetAmountInput: String = "",
    val isTargetAmountInvalid: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: UiText? = null,
    val currency: Currency = Currency.NGN,
    val isCurrencyLoaded: Boolean = false,
) {
    val hasTargetAmountError: Boolean
        get() = isTargetAmountInvalid || (
            targetAmountInput.isNotBlank() && targetAmountInput != "." &&
                moneyInputValue(targetAmountInput, currency) == null
            )

    /** Save is enabled only for a complete, currency-valid target. */
    val canSave: Boolean
        get() = !isSaving && isCurrencyLoaded && !isTargetAmountInvalid &&
            moneyInputValue(targetAmountInput, currency) != null
}
