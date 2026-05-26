package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.CustomMeasurementFieldDto
import com.danzucker.stitchpad.core.domain.model.CustomMeasurementField
import com.danzucker.stitchpad.core.domain.model.CustomerGender

fun CustomMeasurementFieldDto.toCustomMeasurementField(): CustomMeasurementField =
    CustomMeasurementField(
        id = id,
        label = label,
        genders = genders.mapNotNull { raw ->
            runCatching { CustomerGender.valueOf(raw) }.getOrNull()
        }.toSet(),
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun CustomMeasurementField.toCustomMeasurementFieldDto(): CustomMeasurementFieldDto =
    CustomMeasurementFieldDto(
        id = id,
        label = label,
        genders = genders.map { it.name },
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
