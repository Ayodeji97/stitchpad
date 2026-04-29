package com.danzucker.stitchpad.feature.reports.domain.model

data class RevenueSummary(
    val current: Double,
    val previous: Double,
    val deltaAmount: Double,
    val deltaPercent: Double?,
    val sparkline: List<Double>
)
