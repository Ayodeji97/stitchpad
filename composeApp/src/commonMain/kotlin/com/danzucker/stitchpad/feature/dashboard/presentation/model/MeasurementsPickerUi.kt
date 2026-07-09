package com.danzucker.stitchpad.feature.dashboard.presentation.model

/**
 * One row in the Dashboard measurements picker sheet — one per customer.
 *
 * @param measurementCount Total measurement profiles saved for this customer,
 *   or null when the per-customer fetch failed. A null count is treated as
 *   "unknown" (never as zero) — the row shows no count subtitle and routes
 *   to the customer's detail view rather than the misleading "Add" affordance
 *   or destructive create-flow a false zero would trigger.
 * @param singleMeasurementId Set only when [measurementCount] == 1 — lets the
 *   row route straight to the detail view instead of the customer's full list.
 */
data class MeasurementsPickerRow(
    val customerId: String,
    val name: String,
    val measurementCount: Int?,
    val singleMeasurementId: String?,
)

/**
 * State for the Dashboard "Measurement" shortcut's customer picker sheet.
 *
 * Null in [DashboardState.measurementsPicker] means the sheet is closed.
 * [isLoading] covers the window between opening the sheet and the
 * per-customer measurement counts finishing their fetch.
 */
data class MeasurementsPickerUi(
    val isLoading: Boolean = true,
    val query: String = "",
    val rows: List<MeasurementsPickerRow> = emptyList(),
) {
    val filteredRows: List<MeasurementsPickerRow>
        get() = if (query.isBlank()) rows else rows.filter { it.name.contains(query, ignoreCase = true) }
}
