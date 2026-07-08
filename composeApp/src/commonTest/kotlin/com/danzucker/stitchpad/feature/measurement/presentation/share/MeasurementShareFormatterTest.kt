package com.danzucker.stitchpad.feature.measurement.presentation.share

import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeasurementShareFormatterTest {

    private fun measurement(
        fields: Map<String, Double> = mapOf("shoulder_width" to 15.0, "waist" to 31.0, "trouser_waist" to 31.5),
        notes: String? = "Loose at the hip.",
        dateTaken: Long = 1_750_000_000_000L,
    ) = Measurement(
        id = "m1", customerId = "c1", gender = CustomerGender.FEMALE, name = "Wedding gown",
        fields = fields, unit = MeasurementUnit.INCHES, notes = notes,
        dateTaken = dateTaken, createdAt = 1L,
    )

    private fun format(m: Measurement = measurement()) = MeasurementShareFormatter.format(
        measurement = m,
        customerName = "Chidinma Eze",
        measurementName = "Wedding gown",
        genderLabel = "Women's",
        unitLabel = "Inches",
        unitSuffix = "″",
        dateFormatted = "12 Jun 2026",
        businessName = "Zucker Styles",
        customFieldLabels = emptyMap(),
        sectionTitles = mapOf(
            "section_upper_body" to "Upper Body",
            "section_body_lengths" to "Body Lengths",
            "section_trouser" to "Trouser",
        ),
        customSectionTitle = "Custom",
    )

    @Test
    fun `format groups values into localized sections in template order`() {
        val data = format()
        assertEquals(listOf("Upper Body", "Trouser"), data.sections.map { it.title })
        assertEquals(listOf("Shoulder" to "15", "Waist" to "31"), data.sections[0].rows.map { it.label to it.value })
        assertEquals(listOf("Waist" to "31.5"), data.sections[1].rows.map { it.label to it.value })
    }

    @Test
    fun `format falls back to raw titleKey when a localized title is missing`() {
        val data = MeasurementShareFormatter.format(
            measurement = measurement(),
            customerName = "C", measurementName = "M", genderLabel = "G", unitLabel = "U",
            unitSuffix = "″", dateFormatted = null, businessName = null,
            customFieldLabels = emptyMap(), sectionTitles = emptyMap(), customSectionTitle = "Custom",
        )
        assertEquals(listOf("section_upper_body", "section_trouser"), data.sections.map { it.title })
    }

    @Test
    fun `whatsapp text has bold header sections and footer`() {
        val text = MeasurementShareFormatter.buildWhatsAppText(format())
        assertTrue(text.startsWith("📏 *Chidinma Eze — Wedding gown*"))
        assertTrue(text.contains("Women's · Inches · 12 Jun 2026"))
        assertTrue(text.contains("*Upper Body*"))
        assertTrue(text.contains("Shoulder: 15"))
        assertTrue(text.contains("Waist: 31.5"))
        assertTrue(text.contains("_Loose at the hip._"))
        assertTrue(text.trimEnd().endsWith("_Sent from StitchPad · getstitchpad.com_"))
    }

    @Test
    fun `whatsapp text omits date and notes when absent`() {
        val data = format(measurement(notes = null)).copy(dateFormatted = null)
        val text = MeasurementShareFormatter.buildWhatsAppText(data)
        assertTrue(text.contains("Women's · Inches\n"))
        assertFalse(text.contains("· 12 Jun"))
        assertFalse(text.contains("_Loose"))
    }

    @Test
    fun `formatShareDate returns null for legacy zero epoch`() {
        assertNull(MeasurementShareFormatter.formatShareDate(0L))
        assertEquals("12 Jun 2026", MeasurementShareFormatter.formatShareDate(1_781_222_400_000L))
    }
}
