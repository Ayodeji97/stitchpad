package com.danzucker.stitchpad.feature.reports.domain.model

import kotlinx.datetime.LocalDate

/**
 * User-picked date range for [ReportsPeriod.CUSTOM]. Both endpoints are inclusive
 * calendar dates; the [start, end+1day) window is computed by the reports machinery
 * to align with how Week / Month windows are resolved.
 */
data class CustomRange(
    val start: LocalDate,
    val end: LocalDate
) {
    init {
        require(end >= start) { "CustomRange end ($end) must be on or after start ($start)" }
    }
}
