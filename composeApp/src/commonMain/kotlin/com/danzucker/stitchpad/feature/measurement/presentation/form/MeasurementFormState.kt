package com.danzucker.stitchpad.feature.measurement.presentation.form

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
) {
    /**
     * PTSP-6: Save is gated until at least one parsable figure is entered.
     * `toDoubleOrNull()` rejects empty strings and lone `.` (which the field's
     * input filter allows but `save()` would collapse to 0.0 and discard).
     * Edit-mode entries pre-populate `fields` from the existing measurement,
     * so the gate naturally allows resaves of an existing record.
     */
    val canSave: Boolean
        get() = gender != null &&
            fields.values.any { it.toDoubleOrNull() != null } &&
            !isLoading
}
