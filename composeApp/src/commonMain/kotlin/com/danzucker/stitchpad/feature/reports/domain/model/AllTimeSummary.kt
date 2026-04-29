package com.danzucker.stitchpad.feature.reports.domain.model

data class AllTimeSummary(
    val totalCollected: Double,
    val orderCount: Int,
    val topCustomerName: String?,
    val topCustomerTotal: Double
)
