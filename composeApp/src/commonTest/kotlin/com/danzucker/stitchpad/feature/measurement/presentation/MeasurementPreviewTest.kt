package com.danzucker.stitchpad.feature.measurement.presentation

import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeasurementPreviewTest {

    private fun measurement(
        gender: CustomerGender = CustomerGender.FEMALE,
        fields: Map<String, String>,
    ) = Measurement(
        id = "m1",
        customerId = "c1",
        gender = gender,
        fields = fields,
        unit = MeasurementUnit.INCHES,
        notes = null,
        dateTaken = 0L,
        createdAt = 0L,
    )

    @Test
    fun returnsOnlyFilledFields_notGarmentExpectedEmptyOnes() {
        // Only "waist" filled — preview is exactly that one field, nothing else.
        val preview = measurement(fields = mapOf("waist" to "30"))
            .filledPreviewFields(customFieldLabels = emptyMap())
        assertEquals(listOf(MeasurementPreviewField("Waist", "30")), preview)
    }

    @Test
    fun templateFieldsComeBackInTemplateOrder_regardlessOfMapOrder() {
        val preview = measurement(
            // deliberately out of template order in the map
            fields = mapOf("hip_circumference" to "40", "waist" to "30", "bust_circumference" to "36"),
        ).filledPreviewFields(customFieldLabels = emptyMap())
        assertEquals(
            listOf(
                MeasurementPreviewField("Bust", "36"),
                MeasurementPreviewField("Waist", "30"),
                MeasurementPreviewField("Hip", "40"),
            ),
            preview,
        )
    }

    @Test
    fun customFieldsResolveLabelsAndComeAfterTemplate() {
        val preview = measurement(
            fields = mapOf("waist" to "30", "custom-uuid-1" to "12.5"),
        ).filledPreviewFields(customFieldLabels = mapOf("custom-uuid-1" to "Sleeve flare"))
        assertEquals(
            listOf(
                MeasurementPreviewField("Waist", "30"),
                MeasurementPreviewField("Sleeve flare", "12.5"),
            ),
            preview,
        )
    }

    @Test
    fun segmentedValuesArePreservedInPreview() {
        // The core fix: a comma-separated segmented length previews verbatim.
        val preview = measurement(fields = mapOf("full_length_gown" to "40, 45, 56"))
            .filledPreviewFields(customFieldLabels = emptyMap())
        assertEquals(listOf(MeasurementPreviewField("Full length of gown", "40, 45, 56")), preview)
    }

    @Test
    fun skipsZeroValuesAndUnknownCustomKeys() {
        val preview = measurement(
            fields = mapOf("bust_circumference" to "0", "waist" to "30", "orphan-uuid" to "9"),
        ).filledPreviewFields(customFieldLabels = emptyMap())
        // bust is 0 (excluded), orphan-uuid has no label (excluded) — only Waist remains.
        assertEquals(listOf(MeasurementPreviewField("Waist", "30")), preview)
    }

    @Test
    fun capsToMaxKeepingTemplateOrder() {
        val preview = measurement(
            fields = mapOf(
                "bust_circumference" to "36",
                "waist" to "30",
                "hip_circumference" to "40",
                "sleeve_length" to "22",
            ),
        ).filledPreviewFields(customFieldLabels = emptyMap(), max = 2)
        assertEquals(
            listOf(
                MeasurementPreviewField("Bust", "36"),
                MeasurementPreviewField("Waist", "30"),
            ),
            preview,
        )
    }

    @Test
    fun customOnlyMeasurementStillPreviews() {
        val preview = measurement(
            fields = mapOf("c-a" to "5", "c-b" to "7"),
        ).filledPreviewFields(customFieldLabels = mapOf("c-a" to "Ankle", "c-b" to "Bicep"))
        // Custom fields sorted alphabetically by label.
        assertEquals(
            listOf(
                MeasurementPreviewField("Ankle", "5"),
                MeasurementPreviewField("Bicep", "7"),
            ),
            preview,
        )
    }

    @Test
    fun sanitizeMeasurementInput_keepsDigitsDotsCommasAndSpaces() {
        assertEquals("40, 45, 56", sanitizeMeasurementInput("40, 45, 56"))
        assertEquals("16.5", sanitizeMeasurementInput("16.5"))
        // Letters and stray punctuation are stripped as the tailor types.
        assertEquals("40, 45", sanitizeMeasurementInput("40a, 45!"))
    }

    @Test
    fun isPersistableMeasurementValue_matchesTheOldPositiveNumberGate() {
        assertTrue(isPersistableMeasurementValue("36"))
        assertTrue(isPersistableMeasurementValue("0.5"))
        assertTrue(isPersistableMeasurementValue("40, 45, 56"))
        assertFalse(isPersistableMeasurementValue(""))
        assertFalse(isPersistableMeasurementValue("."))
        assertFalse(isPersistableMeasurementValue("0"))
        assertFalse(isPersistableMeasurementValue("0.0"))
    }
}
