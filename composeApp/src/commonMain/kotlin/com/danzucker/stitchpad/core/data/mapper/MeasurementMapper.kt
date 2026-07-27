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
    name = name,
    // Prefer the free-text values; fall back to legacy numeric fields for records
    // written before the punctuation fix, formatting each to a string (36.0 -> "36").
    fields = if (fieldValues.isNotEmpty()) fieldValues else fields.toStringValues(),
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
        name = name,
        bodyShape = null,
        // Write the string values; leave the legacy numeric map empty (new records
        // never use it). FirebaseMeasurementRepository.updateMeasurement replaces
        // both maps wholesale so stale doubles / cleared keys don't survive a merge.
        fields = emptyMap(),
        fieldValues = fields,
        unit = unit.name,
        notes = notes,
        dateTaken = if (dateTaken == 0L) now else dateTaken,
        createdAt = if (createdAt == 0L) now else createdAt,
        updatedAt = now
    )
}

/**
 * Formats legacy numeric field values as strings, dropping a trailing ".0"
 * (36.0 -> "36", 16.5 -> "16.5"). Zero / negative values are dropped — the save
 * pipeline never persisted them, so they only appear as noise on old records.
 */
private fun Map<String, Double>.toStringValues(): Map<String, String> =
    filterValues { it > 0.0 }
        .mapValues { (_, value) ->
            if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
        }

/**
 * Parses gender from the stored value, falling back to inference from legacy garmentType
 * for Sprint 2 records that predate the body profile redesign.
 */
private fun parseGender(genderValue: String, legacyGarmentType: String?): CustomerGender {
    runCatching { CustomerGender.valueOf(genderValue) }.getOrNull()?.let { return it }
    return when (legacyGarmentType?.uppercase()) {
        "AGBADA", "SENATOR_KAFTAN", "SENATOR", "KAFTAN", "DANSHIKI", "VINTAGE", "SHIRT", "SUIT" -> CustomerGender.MALE
        else -> CustomerGender.FEMALE
    }
}
