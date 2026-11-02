package com.danzucker.stitchpad.feature.reports.domain.model

import com.danzucker.stitchpad.core.domain.model.Currency
data class CustomerRanking(
    val customerId: String,
    val customerName: String,
    val totalCollected: Double,
    val orderCount: Int,
    val badge: CustomerBadge = CustomerBadge.NONE,
    val currency: Currency = Currency.NGN,
)
