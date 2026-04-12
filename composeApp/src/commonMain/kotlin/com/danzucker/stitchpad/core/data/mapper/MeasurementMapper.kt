package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.MeasurementDto
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import kotlin.time.Clock

fun MeasurementDto.toMeasurement(customerId: String): Measurement = Measurement(
    id = id,
    customerId = customerId,
    garmentType = runCatching { GarmentType.valueOf(garmentType) }.getOrDefault(GarmentType.DRESS),
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
        garmentType = garmentType.name,
        fields = fields,
        unit = unit.name,
        notes = notes,
        dateTaken = if (dateTaken == 0L) now else dateTaken,
        createdAt = if (createdAt == 0L) now else createdAt,
        updatedAt = now
    )
}
