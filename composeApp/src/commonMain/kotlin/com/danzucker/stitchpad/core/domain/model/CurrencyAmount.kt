package com.danzucker.stitchpad.core.domain.model

/** A major-unit total with its denomination; amounts in different currencies are never added. */
data class CurrencyAmount(val currency: Currency, val amount: Double)

fun <T> sumByCurrency(
    items: List<T>,
    currency: (T) -> Currency,
    amount: (T) -> Double,
): List<CurrencyAmount> = items.groupBy(currency)
    .map { (denomination, group) -> CurrencyAmount(denomination, group.sumOf(amount)) }
    .sortedBy { it.currency.code }
