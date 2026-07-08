package com.danzucker.stitchpad.feature.measurement.presentation.share

import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.sharing.MeasurementShareData
import com.danzucker.stitchpad.core.sharing.MeasurementShareRow
import com.danzucker.stitchpad.core.sharing.MeasurementShareSection
import com.danzucker.stitchpad.feature.measurement.presentation.detail.measurementDetailSections
import com.danzucker.stitchpad.feature.measurement.presentation.formatMeasurementValue
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Builds the share payload for a measurement. Pure: every label arrives
 * pre-localized (the ViewModel resolves string resources), so this object is
 * unit-testable without resource loading. Mirrors [ReceiptFormatter]'s role
 * for order receipts.
 */
object MeasurementShareFormatter {

    fun format(
        measurement: Measurement,
        customerName: String,
        measurementName: String,
        genderLabel: String,
        unitLabel: String,
        unitSuffix: String,
        dateFormatted: String?,
        businessName: String?,
        customFieldLabels: Map<String, String>,
        sectionTitles: Map<String, String>,
        customSectionTitle: String,
    ): MeasurementShareData {
        val sections = measurementDetailSections(measurement, customFieldLabels).map { section ->
            MeasurementShareSection(
                title = when (section.titleKey) {
                    null -> customSectionTitle
                    else -> sectionTitles[section.titleKey] ?: section.titleKey
                },
                rows = section.rows.map { MeasurementShareRow(it.label, formatMeasurementValue(it.value)) },
            )
        }
        return MeasurementShareData(
            customerName = customerName,
            measurementName = measurementName,
            genderLabel = genderLabel,
            unitLabel = unitLabel,
            unitSuffix = unitSuffix,
            dateFormatted = dateFormatted,
            businessName = businessName,
            sections = sections,
            notes = measurement.notes?.takeIf { it.isNotBlank() },
        )
    }

    /** WhatsApp markup: *bold*, _italic_. Values intentionally carry no unit suffix (header names the unit). */
    fun buildWhatsAppText(data: MeasurementShareData): String = buildString {
        append("📏 *").append(data.customerName).append(" — ").append(data.measurementName).appendLine("*")
        append(data.genderLabel).append(" · ").append(data.unitLabel)
        data.dateFormatted?.let { append(" · ").append(it) }
        appendLine()
        data.sections.forEach { section ->
            appendLine()
            append("*").append(section.title).appendLine("*")
            section.rows.forEach { row -> append(row.label).append(": ").appendLine(row.value) }
        }
        data.notes?.let {
            appendLine()
            append("_").append(it).appendLine("_")
        }
        appendLine()
        append("_Sent from StitchPad · getstitchpad.com_")
    }

    /** "12 Jun 2026"; null for the 0L legacy sentinel — same policy as the detail screen's Taken chip. */
    fun formatShareDate(epochMillis: Long): String? {
        if (epochMillis <= 0L) return null
        val date = Instant.fromEpochMilliseconds(epochMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val month = date.month.name.lowercase().replaceFirstChar(Char::uppercase).take(3)
        return "${date.day} $month ${date.year}"
    }
}
