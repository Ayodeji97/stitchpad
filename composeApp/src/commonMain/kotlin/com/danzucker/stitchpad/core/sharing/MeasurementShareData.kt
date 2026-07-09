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
)
