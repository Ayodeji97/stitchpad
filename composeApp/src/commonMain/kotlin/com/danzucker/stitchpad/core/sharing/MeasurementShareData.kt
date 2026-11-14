package com.danzucker.stitchpad.core.sharing

/** One card/text section, labels and title already localized. */
data class MeasurementShareSection(val title: String, val rows: List<MeasurementShareRow>)

/** [value] is pre-formatted (trailing .0 dropped) WITHOUT the unit suffix — renderers append it. */
data class MeasurementShareRow(val label: String, val value: String)

/** Everything the platform renderers and the WhatsApp text builder need — no resource lookups downstream. */
data class MeasurementShareData(
    val customerName: String,
    val measurementName: String,
    val genderLabel: String,
    val unitLabel: String,
    val unitSuffix: String,
    val dateFormatted: String?,
    val businessName: String?,
    val sections: List<MeasurementShareSection>,
    val notes: String?,
    /** Localized fixed copy (card title, "Taken …", footers). Defaults to English. */
    val chrome: MeasurementShareChrome = MeasurementShareChrome(),
)

/**
 * Fixed, non-measurement copy printed on the shared card / WhatsApp text,
 * pre-localized by the caller. Defaults are the English strings the share has
 * always used, so tests and unlocalized callers stay byte-identical.
 */
data class MeasurementShareChrome(
    val cardTitle: String = "MEASUREMENT CARD",
    /** Raw positional template, e.g. "Taken %1$s"; filled by [taken]. */
    val takenTemplate: String = "Taken %1\$s",
    val cardFooter: String = "Made with StitchPad — the smart work pad for tailors · getstitchpad.com",
    /** WhatsApp sign-off, without the `_…_` italic markup (added by the text builder). */
    val whatsAppFooter: String = "Sent from StitchPad · getstitchpad.com",
) {
    fun taken(dateFormatted: String): String = fillTemplate(takenTemplate, dateFormatted)
}
