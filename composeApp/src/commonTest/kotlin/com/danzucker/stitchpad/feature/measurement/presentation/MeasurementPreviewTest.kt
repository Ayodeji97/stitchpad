package com.danzucker.stitchpad.feature.measurement.presentation

import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class MeasurementPreviewTest {

    private fun measurement(
        gender: CustomerGender = CustomerGender.FEMALE,
        fields: Map<String, Double>,
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
        val preview = measurement(fields = mapOf("waist" to 30.0))
            .filledPreviewFields(customFieldLabels = emptyMap())
        assertEquals(listOf(MeasurementPreviewField("Waist", 30.0)), preview)
    }

    @Test
    fun templateFieldsComeBackInTemplateOrder_regardlessOfMapOrder() {
        val preview = measurement(
            // deliberately out of template order in the map
            fields = mapOf("hip_circumference" to 40.0, "waist" to 30.0, "bust_circumference" to 36.0),
        ).filledPreviewFields(customFieldLabels = emptyMap())
        assertEquals(
            listOf(
                MeasurementPreviewField("Bust", 36.0),
                MeasurementPreviewField("Waist", 30.0),
                MeasurementPreviewField("Hip", 40.0),
            ),
            preview,
        )
    }

    @Test
    fun customFieldsResolveLabelsAndComeAfterTemplate() {
        val preview = measurement(
            fields = mapOf("waist" to 30.0, "custom-uuid-1" to 12.5),
        ).filledPreviewFields(customFieldLabels = mapOf("custom-uuid-1" to "Sleeve flare"))
        assertEquals(
            listOf(
                MeasurementPreviewField("Waist", 30.0),
                MeasurementPreviewField("Sleeve flare", 12.5),
            ),
            preview,
        )
    }

    @Test
    fun skipsZeroValuesAndUnknownCustomKeys() {
        val preview = measurement(
            fields = mapOf("bust_circumference" to 0.0, "waist" to 30.0, "orphan-uuid" to 9.0),
        ).filledPreviewFields(customFieldLabels = emptyMap())
        // bust is 0 (excluded), orphan-uuid has no label (excluded) — only Waist remains.
        assertEquals(listOf(MeasurementPreviewField("Waist", 30.0)), preview)
    }

    @Test
    fun capsToMaxKeepingTemplateOrder() {
        val preview = measurement(
            fields = mapOf(
                "bust_circumference" to 36.0,
                "waist" to 30.0,
                "hip_circumference" to 40.0,
                "sleeve_length" to 22.0,
            ),
        ).filledPreviewFields(customFieldLabels = emptyMap(), max = 2)
        assertEquals(
            listOf(
                MeasurementPreviewField("Bust", 36.0),
                MeasurementPreviewField("Waist", 30.0),
            ),
            preview,
        )
    }

    @Test
    fun customOnlyMeasurementStillPreviews() {
        val preview = measurement(
            fields = mapOf("c-a" to 5.0, "c-b" to 7.0),
        ).filledPreviewFields(customFieldLabels = mapOf("c-a" to "Ankle", "c-b" to "Bicep"))
        // Custom fields sorted alphabetically by label.
        assertEquals(
            listOf(
                MeasurementPreviewField("Ankle", 5.0),
                MeasurementPreviewField("Bicep", 7.0),
            ),
            preview,
        )
    }

    @Test
    fun formatMeasurementValue_dropsTrailingZero() {
        assertEquals("36", formatMeasurementValue(36.0))
        assertEquals("36.5", formatMeasurementValue(36.5))
    }
}
