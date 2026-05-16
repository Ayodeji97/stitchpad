package com.danzucker.stitchpad.feature.smart.presentation.draft

import com.danzucker.stitchpad.feature.smart.domain.model.OrderSummary

interface OpenOrdersProvider {
    suspend fun openOrdersFor(customerId: String): List<OrderSummary>
}
