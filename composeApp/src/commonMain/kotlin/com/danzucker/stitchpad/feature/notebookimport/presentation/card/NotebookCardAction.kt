package com.danzucker.stitchpad.feature.notebookimport.presentation.card

import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import kotlinx.datetime.LocalDate

sealed interface NotebookCardAction {
    data class OnNameChange(val name: String) : NotebookCardAction
    data class OnPhoneChange(val phone: String) : NotebookCardAction
    data class OnGenderSelect(val gender: CustomerGender) : NotebookCardAction
    data class OnUnitSelect(val unit: MeasurementUnit) : NotebookCardAction
    data object OnDateClick : NotebookCardAction
    data object OnDateDismiss : NotebookCardAction
    data class OnDatePicked(val date: LocalDate) : NotebookCardAction
    data object OnDateClear : NotebookCardAction

    /** Typing a value clears its "unclear" flag. */
    data class OnValueChange(val index: Int, val value: String) : NotebookCardAction

    /** The tailor says the unclear value was read right. */
    data class OnValueConfirm(val index: Int) : NotebookCardAction
    data class OnValueRemove(val index: Int) : NotebookCardAction
    data class OnFieldPickerOpen(val index: Int) : NotebookCardAction
    data object OnFieldPickerDismiss : NotebookCardAction

    /** [fieldKey] null keeps the value as a note. */
    data class OnFieldSelect(val index: Int, val fieldKey: String?) : NotebookCardAction
    data class OnMatchToggle(val addToExisting: Boolean) : NotebookCardAction
    data object OnLooksRightClick : NotebookCardAction
    data object OnSaveNowClick : NotebookCardAction
    data object OnSkipClick : NotebookCardAction
    data object OnNavigateBack : NotebookCardAction
    data object OnMessageShown : NotebookCardAction
}
