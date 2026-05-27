package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.domain.model.CustomMeasurementField
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.data.dto.CustomMeasurementFieldDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CustomMeasurementFieldMapperTest {

    @Test
    fun roundTrip_preservesAllFields() {
        val original = CustomMeasurementField(
            id = "ca3f-7b",
            label = "Sleeve cuff width",
            genders = setOf(CustomerGender.FEMALE, CustomerGender.MALE),
            isArchived = false,
            createdAt = 1_748_275_200_000L,
            updatedAt = 1_748_275_300_000L,
        )

        val dto = original.toCustomMeasurementFieldDto()
        val back = dto.toCustomMeasurementField()

        assertEquals(original.id, back.id)
        assertEquals(original.label, back.label)
        assertEquals(original.genders, back.genders)
        assertEquals(original.isArchived, back.isArchived)
        assertEquals(original.createdAt, back.createdAt)
        assertEquals(original.updatedAt, back.updatedAt)
    }

    @Test
    fun toDto_serializesGendersAsStringList() {
        val field = CustomMeasurementField(
            id = "x",
            label = "L",
            genders = setOf(CustomerGender.MALE),
            createdAt = 0L,
            updatedAt = 0L,
        )

        val dto = field.toCustomMeasurementFieldDto()

        assertEquals(listOf("MALE"), dto.genders)
    }

    @Test
    fun fromDto_unknownGenderString_isFilteredOut() {
        // Future-proofing: if a future client persists a gender we don't know,
        // older clients should ignore the unknown entry rather than crash.
        val dto = CustomMeasurementFieldDto(
            id = "x",
            label = "L",
            genders = listOf("FEMALE", "ALIEN"),
            isArchived = false,
            createdAt = 0L,
            updatedAt = 0L,
        )

        val field = dto.toCustomMeasurementField()

        assertEquals(setOf(CustomerGender.FEMALE), field.genders)
    }

    @Test
    fun fromDto_emptyGenders_yieldsEmptySet() {
        val dto = CustomMeasurementFieldDto(
            id = "x",
            label = "L",
            genders = emptyList(),
            isArchived = false,
            createdAt = 0L,
            updatedAt = 0L,
        )

        assertTrue(dto.toCustomMeasurementField().genders.isEmpty())
    }
}
