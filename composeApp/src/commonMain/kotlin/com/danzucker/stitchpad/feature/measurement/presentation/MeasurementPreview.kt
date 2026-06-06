package com.danzucker.stitchpad.feature.measurement.presentation

import com.danzucker.stitchpad.core.domain.model.BodyProfileTemplate
import com.danzucker.stitchpad.core.domain.model.Measurement

/**
 * The fields the tailor actually filled on this measurement, resolved to display
 * labels. Body-profile template fields come first (in template order, essentials
 * first), then custom fields (alphabetical). Only values that are present and
 * greater than zero are included — so a measurement with two filled fields
 * previews exactly those two, never the garment's expected-but-empty fields.
 *
 * Custom-field keys (UUIDs) resolve via [customFieldLabels]; keys absent from the
 * template and from [customFieldLabels] are skipped. When [max] is non-null the
 * result is capped to that many fields (the "top N" preview).
 */
fun Measurement.filledPreviewFields(
    customFieldLabels: Map<String, String>,
    max: Int? = null,
): List<MeasurementPreviewField> {
    val templateFields = BodyProfileTemplate.sectionsFor(gender).flatMap { it.fields }
    val templateKeys = templateFields.mapTo(mutableSetOf()) { it.key }

    val templatePreview = templateFields.mapNotNull { field ->
        fields[field.key]
            ?.takeIf { it > 0.0 }
            ?.let { MeasurementPreviewField(field.label, it) }
    }

    val customPreview = fields
        .filter { (key, value) -> key !in templateKeys && key in customFieldLabels && value > 0.0 }
        .map { (key, value) -> MeasurementPreviewField(customFieldLabels.getValue(key), value) }
        .sortedBy { it.label.lowercase() }

    val all = templatePreview + customPreview
    return if (max != null) all.take(max) else all
}

/** Formats a measurement value, dropping a trailing ".0" (e.g. 36.0 -> "36", 36.5 -> "36.5"). */
fun formatMeasurementValue(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
