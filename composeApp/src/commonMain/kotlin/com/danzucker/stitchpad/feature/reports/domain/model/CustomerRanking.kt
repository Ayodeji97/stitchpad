package com.danzucker.stitchpad.feature.reports.domain.model

data class CustomerRanking(
    val customerId: String,
    val customerName: String,
    val totalCollected: Double,
    val orderCount: Int
)
