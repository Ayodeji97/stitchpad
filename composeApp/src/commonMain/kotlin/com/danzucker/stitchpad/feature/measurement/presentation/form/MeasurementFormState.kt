package com.danzucker.stitchpad.feature.measurement.presentation.form

import com.danzucker.stitchpad.core.domain.model.CustomMeasurementField
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.MeasurementSection
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.presentation.UiText

data class MeasurementFormState(
    val gender: CustomerGender? = null,
    val sections: List<MeasurementSection> = emptyList(),
    val currentSectionIndex: Int = 0,
    val isCurrentSectionExpanded: Boolean = true,
    val isNotesExpanded: Boolean = false,
    val fields: Map<String, String> = emptyMap(),
    val unit: MeasurementUnit = MeasurementUnit.INCHES,
    val notes: String = "",
    val isLoading: Boolean = false,
    val isEditMode: Boolean = false,
    val errorMessage: UiText? = null,
    val originalCreatedAt: Long = 0L,
    val originalDateTaken: Long = 0L,
    // PTSP-12 additions
    val customFields: List<CustomMeasurementField> = emptyList(),
    val canUseCustomMeasurements: Boolean = false,
    val isInWelcomeWindow: Boolean = false,  // PTSP-12: drives "First Month" pill copy
    val customFieldSheet: CustomFieldSheet? = null,
) {
    /**
     * PTSP-6: Save is gated to mirror what `MeasurementFormViewModel.save()`
     * will actually persist — at least one field that parses to a positive
     * double. The save pipeline drops empty strings, lone `.`, unparsable
     * input, and zero values, so any of those alone would silently produce
     * an empty measurement if the gate didn't agree.
     *
     * Edit-mode entries pre-populate `fields` from the existing measurement,
     * so the gate naturally allows resaves of an existing record.
     */
    val canSave: Boolean
        get() = gender != null &&
            fields.values.any { (it.toDoubleOrNull() ?: 0.0) > 0.0 } &&
            !isLoading
}

sealed interface CustomFieldSheet {
    /** "Add custom field" — empty form, no existing field. */
    data object Adding : CustomFieldSheet

    /** "Edit custom field" — pre-populated from an existing field. */
    data class Editing(val field: CustomMeasurementField) : CustomFieldSheet

    /** "Archive this field?" confirm dialog, holding the field to archive. */
    data class ConfirmArchive(val field: CustomMeasurementField) : CustomFieldSheet
}
