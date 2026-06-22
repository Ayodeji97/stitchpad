package com.danzucker.stitchpad.feature.measurement.presentation.form

import com.danzucker.stitchpad.core.domain.model.CustomMeasurementField
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.MeasurementSection
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.presentation.UiText

data class MeasurementFormState(
    val gender: CustomerGender? = null,
    val name: String = "",
    /** True once the tailor edits the name; stops the gender-driven default from overwriting it. */
    val isNameUserEdited: Boolean = false,
    /** 1-based position used to build the distinct default name. */
    val nameOrdinal: Int = 1,
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
    val isInWelcomeWindow: Boolean = false, // PTSP-12: drives "First Month" pill copy
    // PTSP-12: pill distinguishes trial (FREE+welcome) from permanent (Pro/Atelier).
    // Without tier, Pro/Atelier users inside their first 30 days would see
    // "FIRST MONTH" — implying temporary access for what is actually permanent.
    val tier: SubscriptionTier = SubscriptionTier.FREE,
    val customFieldSheet: CustomFieldSheet? = null,
    // True when this form was reached as the second step of customer creation
    // (CustomerForm → "Save & Add Measurements"). Drives the "Save" CTA copy and
    // the "Skip for now" escape hatch. False for edit / order-link / detail entry.
    val fromCustomerCreation: Boolean = false,
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
            name.isNotBlank() &&
            fields.values.any { (it.toDoubleOrNull() ?: 0.0) > 0.0 } &&
            !isLoading
}

sealed interface CustomFieldSheet {
    /** "Add custom field" — empty form, no existing field. */
    data class Adding(val draft: CustomFieldDraft = CustomFieldDraft()) : CustomFieldSheet

    /** "Edit custom field" — pre-populated from an existing field. */
    data class Editing(
        val field: CustomMeasurementField,
        val draft: CustomFieldDraft = CustomFieldDraft.from(field),
    ) : CustomFieldSheet

    /** "Archive this field?" confirm dialog, holding the field to archive. */
    data class ConfirmArchive(val field: CustomMeasurementField) : CustomFieldSheet
}

data class CustomFieldDraft(
    val label: String = "",
    val initialValue: String = "",
    val genders: Set<CustomerGender> = setOf(CustomerGender.FEMALE, CustomerGender.MALE),
) {
    companion object {
        fun from(field: CustomMeasurementField): CustomFieldDraft =
            CustomFieldDraft(
                label = field.label,
                genders = field.genders,
            )
    }
}
