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
            ?.takeIf { isPersistableMeasurementValue(it) }
            ?.let { MeasurementPreviewField(field.label, it) }
    }

    val customPreview = fields
        .filter { (key, value) ->
            key !in templateKeys && key in customFieldLabels && isPersistableMeasurementValue(value)
        }
        .map { (key, value) -> MeasurementPreviewField(customFieldLabels.getValue(key), value) }
        .sortedBy { it.label.lowercase() }

    val all = templatePreview + customPreview
    return if (max != null) all.take(max) else all
}

/**
 * Keeps only the characters valid in a measurement value: digits, decimal points,
 * commas, and spaces. Tailors enter segmented lengths ("40, 45, 56") and half
 * sizes ("16.5"), so commas and multiple decimal points must survive — everything
 * else (letters, other punctuation) is stripped as the tailor types.
 */
fun sanitizeMeasurementInput(raw: String): String =
    raw.filter { it.isDigit() || it == '.' || it == ',' || it == ' ' }

/**
 * True when a measurement value is worth persisting / showing. Mirrors the old
 * "> 0.0" gate for plain numbers (drops "", ".", "0", "0.0", keeps "0.5", "36")
 * while accepting free-form segmented values ("40, 45, 56") — anything that
 * carries at least one meaningful digit.
 */
fun isPersistableMeasurementValue(value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return false
    val asNumber = trimmed.toDoubleOrNull()
    return if (asNumber != null) asNumber > 0.0 else trimmed.any { it.isDigit() }
}
