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
    val errorMessage: UiText? = null
)
