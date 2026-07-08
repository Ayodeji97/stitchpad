package com.danzucker.stitchpad.feature.measurement.presentation.detail

import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeasurementDetailSectionsTest {

    private fun measurement(
        gender: CustomerGender = CustomerGender.FEMALE,
        fields: Map<String, Double>,
    ) = Measurement(
        id = "m1",
        customerId = "c1",
        gender = gender,
        name = "Test",
        fields = fields,
        unit = MeasurementUnit.INCHES,
        notes = null,
        dateTaken = 0L,
        createdAt = 0L,
    )

    @Test
    fun `groups filled values under their template sections in template order`() {
        val sections = measurementDetailSections(
            measurement = measurement(
                fields = mapOf(
                    "trouser_waist" to 31.0,
                    "shoulder_width" to 15.0,
                    "bust_circumference" to 38.0,
                ),
            ),
            customFieldLabels = emptyMap(),
        )
        assertEquals(listOf("section_upper_body", "section_trouser"), sections.map { it.titleKey })
        assertEquals(listOf("Shoulder", "Bust"), sections[0].rows.map { it.label })
        assertEquals(listOf(15.0, 38.0), sections[0].rows.map { it.value })
    }

    @Test
    fun `drops zero and missing values and omits empty sections`() {
        val sections = measurementDetailSections(
            measurement = measurement(fields = mapOf("waist" to 31.0, "hip_circumference" to 0.0)),
            customFieldLabels = emptyMap(),
        )
        assertEquals(1, sections.size)
        assertEquals("section_upper_body", sections[0].titleKey)
        assertEquals(listOf("Waist"), sections[0].rows.map { it.label })
    }

    @Test
    fun `custom fields come last as a null-titleKey group sorted alphabetically`() {
        val sections = measurementDetailSections(
            measurement = measurement(
                fields = mapOf("waist" to 31.0, "uuid-b" to 12.0, "uuid-a" to 7.5),
            ),
            customFieldLabels = mapOf("uuid-a" to "Zip length", "uuid-b" to "Agbada flare"),
        )
        val custom = sections.last()
        assertNull(custom.titleKey)
        assertEquals(listOf("Agbada flare", "Zip length"), custom.rows.map { it.label })
    }

    @Test
    fun `orphan keys without a label are skipped`() {
        val sections = measurementDetailSections(
            measurement = measurement(fields = mapOf("waist" to 31.0, "unknown-key" to 9.0)),
            customFieldLabels = emptyMap(),
        )
        assertTrue(sections.flatMap { it.rows }.none { it.value == 9.0 })
    }
}
