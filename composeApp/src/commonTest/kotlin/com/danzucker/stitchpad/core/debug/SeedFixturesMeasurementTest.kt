package com.danzucker.stitchpad.core.debug

import com.danzucker.stitchpad.core.domain.model.BodyProfileTemplate
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.feature.measurement.presentation.detail.measurementDetailSections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the seeded measurements against key drift. Seed fixtures once stored
 * display labels ("Bust") instead of BodyProfileTemplate keys
 * ("bust_circumference"); every value was then silently dropped by the detail
 * screen, which renders a blank body profile. These tests fail loudly if a
 * fixture key stops matching the template.
 */
class SeedFixturesMeasurementTest {

    private val customers = SeedFixtures.customers(userId = "u1", now = 1_000L)

    private fun templateKeysFor(measurement: Measurement): Set<String> =
        BodyProfileTemplate.sectionsFor(measurement.gender)
            .flatMap { it.fields }
            .mapTo(mutableSetOf()) { it.key }

    @Test
    fun activeWorkshop_measurement_uses_only_template_keys() {
        val measurement = SeedFixtures.measurementsFor(customers.first(), now = 1_000L)
        val unknown = measurement.fields.keys - templateKeysFor(measurement)

        assertEquals(emptySet(), unknown, "Seeded keys not in the FEMALE template")
    }

    @Test
    fun activeWorkshop_measurement_fills_every_female_template_field() {
        val measurement = SeedFixtures.measurementsFor(customers.first(), now = 1_000L)
        val missing = templateKeysFor(measurement) - measurement.fields.keys

        assertEquals(emptySet(), missing, "Template fields left unseeded")
        assertTrue(measurement.fields.values.all { it > 0.0 }, "Zero values are dropped when rendering")
    }

    @Test
    fun activeWorkshop_measurement_renders_all_sections_on_the_detail_screen() {
        val measurement = SeedFixtures.measurementsFor(customers.first(), now = 1_000L)
        val sections = measurementDetailSections(measurement, customFieldLabels = emptyMap())

        val expected = BodyProfileTemplate.sectionsFor(measurement.gender).map { it.titleKey }
        assertEquals(expected, sections.map { it.titleKey })
    }

    @Test
    fun bulk_measurement_uses_only_template_keys_and_renders() {
        val measurement = SeedFixtures.bulkMeasurementFor(customers.first(), now = 1_000L)
        val unknown = measurement.fields.keys - templateKeysFor(measurement)

        assertEquals(emptySet(), unknown, "Bulk seeded keys not in the FEMALE template")
        assertTrue(
            measurementDetailSections(measurement, customFieldLabels = emptyMap()).isNotEmpty(),
            "Bulk measurement renders a blank detail screen",
        )
    }

    @Test
    fun seeded_measurements_vary_girth_between_customers() {
        val first = SeedFixtures.measurementsFor(customers[0], now = 1_000L)
        val second = SeedFixtures.measurementsFor(customers[1], now = 1_000L)

        assertTrue(
            first.fields.getValue("bust_circumference") != second.fields.getValue("bust_circumference"),
            "Seeded customers should not all share one body profile",
        )
    }
}
