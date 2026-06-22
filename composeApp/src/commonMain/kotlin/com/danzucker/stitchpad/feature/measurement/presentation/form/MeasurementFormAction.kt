package com.danzucker.stitchpad.feature.measurement.presentation.form

import com.danzucker.stitchpad.core.domain.model.CustomerGender

sealed interface MeasurementFormAction {
    data class OnGenderChange(val gender: CustomerGender) : MeasurementFormAction
    data class OnNameChange(val name: String) : MeasurementFormAction
    data class OnNameDefaultApplied(val name: String) : MeasurementFormAction
    data class OnSectionChange(val index: Int) : MeasurementFormAction
    data object OnNextSection : MeasurementFormAction
    data object OnPreviousSection : MeasurementFormAction
    data object OnToggleShowMore : MeasurementFormAction
    data object OnToggleNotes : MeasurementFormAction
    data class OnFieldChange(val key: String, val value: String) : MeasurementFormAction
    data class OnNotesChange(val notes: String) : MeasurementFormAction
    data object OnSaveClick : MeasurementFormAction
    data object OnSkipClick : MeasurementFormAction
    data object OnNavigateBack : MeasurementFormAction
    data object OnErrorDismiss : MeasurementFormAction

    // PTSP-12 — custom measurement fields
    data object OnAddCustomFieldClick : MeasurementFormAction
    data object OnLockedCustomFieldClick : MeasurementFormAction
    data class OnEditCustomFieldClick(val fieldId: String) : MeasurementFormAction
    data object OnCustomFieldSheetDismiss : MeasurementFormAction
    data class OnCustomFieldDraftLabelChange(val label: String) : MeasurementFormAction
    data class OnCustomFieldDraftInitialValueChange(val value: String) : MeasurementFormAction
    data class OnCustomFieldDraftGendersChange(val genders: Set<CustomerGender>) : MeasurementFormAction
    data class OnSaveCustomField(
        val id: String?, // null = create, non-null = update
        val label: String,
        val genders: Set<CustomerGender>,
        val initialValue: String = "",
    ) : MeasurementFormAction
    data class OnArchiveCustomFieldRequest(val fieldId: String) : MeasurementFormAction
    data class OnArchiveCustomFieldConfirm(val fieldId: String) : MeasurementFormAction
}
