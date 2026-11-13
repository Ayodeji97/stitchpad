package com.danzucker.stitchpad.feature.measurement.presentation

import com.danzucker.stitchpad.core.domain.model.BodyProfileTemplate
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.MeasurementField
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.sharing.MeasurementShareRow
import com.danzucker.stitchpad.feature.measurement.presentation.detail.measurementDetailSections
import com.danzucker.stitchpad.feature.measurement.presentation.share.MeasurementShareFormatter
import com.danzucker.stitchpad.feature.measurement.presentation.share.MeasurementShareLabels
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.measurement_field_bust_point
import stitchpad.composeapp.generated.resources.measurement_field_neck
import stitchpad.composeapp.generated.resources.measurement_field_neck_circumference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Resource loading (getString / stringResource) needs a platform context that
 * plain JVM unit tests lack, so these tests cover the resolution contract: which
 * resource a key maps to, and how pre-resolved labels flow through the pure
 * preview / detail / share builders.
 */
class MeasurementFieldLabelTest {

    private val frenchLabels = mapOf(
        "bust_circumference" to "Tour de poitrine",
        "waist" to "Tour de taille",
        "neck_circumference" to "Tour de cou",
        "neck" to "Tour de cou",
    )

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
    fun knownKey_mapsToItsResource() {
        assertEquals(Res.string.measurement_field_bust_point, measurementFieldLabelResource("bust_point"))
    }

    @Test
    fun everyTemplateField_hasALabelResource() {
        CustomerGender.entries.flatMap { BodyProfileTemplate.sectionsFor(it) }
            .flatMap { it.fields }
            .forEach { field ->
                assertTrue(
                    measurementFieldLabelResource(field.labelKey) != null,
                    "Missing measurement_field_${field.labelKey}",
                )
            }
    }

    @Test
    fun sharedNeckKey_resolvesPerGender() {
        val female = BodyProfileTemplate.sectionsFor(CustomerGender.FEMALE).flatMap { it.fields }
            .first { it.key == "neck_circumference" }
        val male = BodyProfileTemplate.sectionsFor(CustomerGender.MALE).flatMap { it.fields }
            .first { it.key == "neck_circumference" }
        assertEquals(Res.string.measurement_field_neck_circumference, measurementFieldLabelResource(female.labelKey))
        assertEquals(Res.string.measurement_field_neck, measurementFieldLabelResource(male.labelKey))
    }

    @Test
    fun unknownKey_hasNoResource_andFallsBackToStoredLabel() {
        assertNull(measurementFieldLabelResource("future_field"))
        val field = MeasurementField("future_field", "Future field", isEssential = false)
        assertEquals("Future field", field.displayLabel(frenchLabels))
    }

    @Test
    fun knownField_usesLocalizedLabel_whenProvided() {
        val field = MeasurementField("waist", "Waist", isEssential = true)
        assertEquals("Tour de taille", field.displayLabel(frenchLabels))
        assertEquals("Waist", field.displayLabel(emptyMap()))
    }

    @Test
    fun customField_keepsTailorLabel_whileTemplateFieldsLocalize() {
        val preview = measurement(fields = mapOf("waist" to "30", "custom-uuid" to "12"))
            .filledPreviewFields(
                customFieldLabels = mapOf("custom-uuid" to "Sleeve flare"),
                templateLabels = frenchLabels,
            )
        assertEquals(
            listOf(MeasurementPreviewField("Tour de taille", "30"), MeasurementPreviewField("Sleeve flare", "12")),
            preview,
        )
    }

    @Test
    fun detailSections_useLocalizedTemplateLabels() {
        val sections = measurementDetailSections(
            measurement(gender = CustomerGender.MALE, fields = mapOf("neck_circumference" to "15")),
            customFieldLabels = emptyMap(),
            templateLabels = frenchLabels,
        )
        assertEquals(listOf(MeasurementPreviewField("Tour de cou", "15")), sections.single().rows)
    }

    @Test
    fun shareFormatter_usesLocalizedFieldLabels() {
        val labels = MeasurementShareLabels(
            measurementName = "Mesures",
            genderLabel = "Femme",
            unitLabel = "Pouces",
            unitSuffix = "″",
            dateFormatted = null,
            businessName = null,
            sectionTitles = emptyMap(),
            customSectionTitle = "Personnalisé",
            fieldLabels = frenchLabels,
        )
        val data = MeasurementShareFormatter.format(
            measurement = measurement(fields = mapOf("bust_circumference" to "36", "custom-uuid" to "4")),
            customerName = "Ada",
            labels = labels,
            customFieldLabels = mapOf("custom-uuid" to "Sleeve flare"),
        )
        val rows = data.sections.flatMap { it.rows }
        assertEquals(listOf(MeasurementShareRow("Tour de poitrine", "36"), MeasurementShareRow("Sleeve flare", "4")), rows)
        assertTrue("Tour de poitrine: 36" in MeasurementShareFormatter.buildWhatsAppText(data))
    }
}
