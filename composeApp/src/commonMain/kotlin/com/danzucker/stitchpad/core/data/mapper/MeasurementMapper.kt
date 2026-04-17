package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.MeasurementDto
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import kotlin.time.Clock

fun MeasurementDto.toMeasurement(customerId: String): Measurement = Measurement(
    id = id,
    customerId = customerId,
    gender = parseGender(gender, garmentType),
    fields = fields,
    unit = runCatching { MeasurementUnit.valueOf(unit) }.getOrDefault(MeasurementUnit.INCHES),
    notes = notes,
    dateTaken = dateTaken,
    createdAt = createdAt
)

fun Measurement.toMeasurementDto(): MeasurementDto {
    val now = Clock.System.now().toEpochMilliseconds()
    return MeasurementDto(
        id = id,
        gender = gender.name,
        bodyShape = null,
        fields = fields,
        unit = unit.name,
        notes = notes,
        dateTaken = if (dateTaken == 0L) now else dateTaken,
        createdAt = if (createdAt == 0L) now else createdAt,
        updatedAt = now
    )
}

/**
 * Parses gender from the stored value, falling back to inference from legacy garmentType
 * for Sprint 2 records that predate the body profile redesign.
 */
private fun parseGender(genderValue: String, legacyGarmentType: String?): CustomerGender {
    runCatching { CustomerGender.valueOf(genderValue) }.getOrNull()?.let { return it }
    return when (legacyGarmentType?.uppercase()) {
        "AGBADA", "SENATOR_KAFTAN", "SHIRT", "SUIT" -> CustomerGender.MALE
        else -> CustomerGender.FEMALE
    }
}
