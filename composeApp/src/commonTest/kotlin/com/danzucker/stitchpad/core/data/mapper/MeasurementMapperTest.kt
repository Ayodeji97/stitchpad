package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.MeasurementDto
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MeasurementMapperTest {

    private fun measurement(fields: Map<String, String>) = Measurement(
        id = "m1",
        customerId = "c1",
        gender = CustomerGender.FEMALE,
        name = "Wedding Agbada",
        fields = fields,
        unit = MeasurementUnit.INCHES,
        notes = null,
        dateTaken = 1L,
        createdAt = 1L,
    )

    @Test
    fun name_roundTrips_throughDto() {
        assertEquals(
            "Wedding Agbada",
            measurement(mapOf("bust" to "36")).toMeasurementDto().toMeasurement("c1").name,
        )
    }

    @Test
    fun legacyDto_withoutName_mapsToEmptyName() {
        val dto = MeasurementDto(id = "m1", gender = "FEMALE", fields = mapOf("bust" to 36.0))
        assertEquals("", dto.toMeasurement("c1").name)
    }

    @Test
    fun freeTextValues_roundTrip_throughDto() {
        // The bug this fix targets: segmented lengths ("40, 45, 56") and half sizes
        // ("16.5") must survive the write/read cycle intact.
        val fields = mapOf("full_length_gown" to "40, 45, 56", "wrist" to "16.5")
        val restored = measurement(fields).toMeasurementDto().toMeasurement("c1")
        assertEquals(fields, restored.fields)
    }

    @Test
    fun newWrite_populatesFieldValues_andClearsLegacyNumericMap() {
        val dto = measurement(mapOf("bust" to "36", "hip" to "40, 45")).toMeasurementDto()
        assertEquals(mapOf("bust" to "36", "hip" to "40, 45"), dto.fieldValues)
        // Legacy numeric map is cleared so a merged update leaves no stale doubles.
        assertTrue(dto.fields.isEmpty())
    }

    @Test
    fun legacyNumericFields_areReadAsFormattedStrings() {
        // A record written before this fix stores numbers in `fields`. It must still
        // deserialize, with each value formatted to a string (36.0 -> "36", drop ".0").
        val dto = MeasurementDto(
            id = "m1",
            gender = "FEMALE",
            fields = mapOf("bust" to 36.0, "wrist" to 16.5),
        )
        assertEquals(mapOf("bust" to "36", "wrist" to "16.5"), dto.toMeasurement("c1").fields)
    }

    @Test
    fun legacyNumericZeroValues_areDropped() {
        val dto = MeasurementDto(
            id = "m1",
            gender = "FEMALE",
            fields = mapOf("bust" to 36.0, "empty" to 0.0),
        )
        assertEquals(mapOf("bust" to "36"), dto.toMeasurement("c1").fields)
    }

    @Test
    fun fieldValues_takePrecedence_overLegacyNumericFields() {
        // A record that carries both (e.g. an old doc merged with a new write) reads
        // the free-text values, not the stale numbers.
        val dto = MeasurementDto(
            id = "m1",
            gender = "FEMALE",
            fields = mapOf("bust" to 99.0),
            fieldValues = mapOf("bust" to "36, 38"),
        )
        assertEquals(mapOf("bust" to "36, 38"), dto.toMeasurement("c1").fields)
    }
}
