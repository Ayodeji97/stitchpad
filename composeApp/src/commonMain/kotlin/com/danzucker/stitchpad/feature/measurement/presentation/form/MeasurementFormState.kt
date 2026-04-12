package com.danzucker.stitchpad.feature.measurement.presentation.form

import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.presentation.UiText

data class MeasurementFormState(
    val garmentType: GarmentType = GarmentType.DRESS,
    val fields: Map<String, String> = emptyMap(),
    val unit: MeasurementUnit = MeasurementUnit.INCHES,
    val notes: String = "",
    val isLoading: Boolean = false,
    val isEditMode: Boolean = false,
    val errorMessage: UiText? = null
)
