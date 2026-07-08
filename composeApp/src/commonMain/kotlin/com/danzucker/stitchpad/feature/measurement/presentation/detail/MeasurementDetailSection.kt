package com.danzucker.stitchpad.feature.measurement.presentation.detail

import com.danzucker.stitchpad.core.domain.model.BodyProfileTemplate
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.feature.measurement.presentation.MeasurementPreviewField

/**
 * One rendered section on the read-only measurement detail screen. [titleKey]
 * is a BodyProfileTemplate section key ("section_upper_body", …) resolved to a
 * string resource at the composable layer; null marks the custom-fields group.
 */
data class MeasurementDetailSection(
    val titleKey: String?,
    val rows: List<MeasurementPreviewField>,
)

/**
 * Groups the measurement's filled values (> 0) into template sections in
 * template order, followed by one custom group (alphabetical) resolved via
 * [customFieldLabels]. Sections with no filled values are dropped. Keys that
 * are neither template fields nor resolvable custom fields are skipped —
 * the same policy as [com.danzucker.stitchpad.feature.measurement.presentation.filledPreviewFields].
 */
fun measurementDetailSections(
    measurement: Measurement,
    customFieldLabels: Map<String, String>,
): List<MeasurementDetailSection> {
    val templateSections = BodyProfileTemplate.sectionsFor(measurement.gender)
    val templateKeys = templateSections.flatMap { it.fields }.mapTo(mutableSetOf()) { it.key }

    val filled = templateSections.mapNotNull { section ->
        val rows = section.fields.mapNotNull { field ->
            measurement.fields[field.key]
                ?.takeIf { it > 0.0 }
                ?.let { MeasurementPreviewField(field.label, it) }
        }
        if (rows.isEmpty()) null else MeasurementDetailSection(section.titleKey, rows)
    }

    val customRows = measurement.fields
        .filter { (key, value) -> key !in templateKeys && key in customFieldLabels && value > 0.0 }
        .map { (key, value) -> MeasurementPreviewField(customFieldLabels.getValue(key), value) }
        .sortedBy { it.label.lowercase() }

    return if (customRows.isEmpty()) {
        filled
    } else {
        filled + MeasurementDetailSection(titleKey = null, rows = customRows)
    }
}
