package com.danzucker.stitchpad.feature.dashboard.domain

import com.danzucker.stitchpad.core.domain.model.Currency
import com.danzucker.stitchpad.core.domain.model.Order

/** Recorded payments, not profit; unpaid balances are kept separate by denomination. */
data class WorkshopEarnings(
    val currency: Currency,
    val collected: Double,
    val remaining: Double,
)

object WorkshopEarningsCalculator {
    fun compute(orders: List<Order>): List<WorkshopEarnings> = orders
        .distinctBy { it.id }
        .groupBy { it.currency }
        .map { (currency, records) ->
            WorkshopEarnings(
                currency = currency,
                collected = records.sumOf { it.depositPaid },
                remaining = records.filter { it.archivedAt == null }.sumOf { it.balanceRemaining },
            )
        }
        .sortedBy { it.currency.code }
}
