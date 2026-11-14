package com.danzucker.stitchpad.feature.measurement.presentation.share

import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.sharing.MeasurementShareChrome
import com.danzucker.stitchpad.core.sharing.MeasurementShareData
import com.danzucker.stitchpad.core.sharing.MeasurementShareRow
import com.danzucker.stitchpad.core.sharing.MeasurementShareSection
import com.danzucker.stitchpad.feature.measurement.presentation.detail.measurementDetailSections
import kotlinx.datetime.Instant
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Pre-localized labels for [MeasurementShareFormatter.format] — resolved by the caller (ViewModel) so the formatter stays resource-free. */
data class MeasurementShareLabels(
    val measurementName: String,
    val genderLabel: String,
    val unitLabel: String,
    val unitSuffix: String,
    val dateFormatted: String?,
    val businessName: String?,
    val sectionTitles: Map<String, String>,
    val customSectionTitle: String,
    /** Card title / "Taken …" / footers. English defaults keep tests and previews resource-free. */
    val chrome: MeasurementShareChrome = MeasurementShareChrome(),
    /** Localized template field names keyed by labelKey; empty = English template labels (tests, previews). */
    val fieldLabels: Map<String, String> = emptyMap(),
)

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
        labels: MeasurementShareLabels,
        customFieldLabels: Map<String, String>,
    ): MeasurementShareData {
        val sections = measurementDetailSections(measurement, customFieldLabels, labels.fieldLabels).map { section ->
            MeasurementShareSection(
                title = when (section.titleKey) {
                    null -> labels.customSectionTitle
                    else -> labels.sectionTitles[section.titleKey] ?: section.titleKey
                },
                rows = section.rows.map { MeasurementShareRow(it.label, it.value) },
            )
        }
        return MeasurementShareData(
            customerName = customerName,
            measurementName = labels.measurementName,
            genderLabel = labels.genderLabel,
            unitLabel = labels.unitLabel,
            unitSuffix = labels.unitSuffix,
            dateFormatted = labels.dateFormatted,
            businessName = labels.businessName,
            sections = sections,
            notes = measurement.notes?.takeIf { it.isNotBlank() },
            chrome = labels.chrome,
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
        append("_").append(data.chrome.whatsAppFooter).append("_")
    }

    /** "12 Jun 2026"; null for the 0L legacy sentinel — same policy as the detail screen's Taken chip. */
    fun formatShareDate(epochMillis: Long, monthNames: List<String> = DEFAULT_SHORT_MONTH_NAMES): String? {
        if (epochMillis <= 0L) return null
        val date = Instant.fromEpochMilliseconds(epochMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val month = monthNames.getOrNull(date.monthNumber - 1) ?: date.month.englishShortName()
        return "${date.day} $month ${date.year}"
    }

    /** English abbreviations used when the caller supplies no localized names (tests, previews). */
    private val DEFAULT_SHORT_MONTH_NAMES = Month.entries.map { it.englishShortName() }

    private fun Month.englishShortName(): String = name.lowercase().replaceFirstChar(Char::uppercase).take(3)
}
