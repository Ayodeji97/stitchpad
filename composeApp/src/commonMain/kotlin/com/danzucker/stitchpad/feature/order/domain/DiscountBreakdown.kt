package com.danzucker.stitchpad.feature.order.domain

import kotlin.math.roundToInt

/**
 * Breakdown of a whole-order discount for the order-form summary. [amount] is the raw typed
 * discount floored at 0 — NOT clamped to the subtotal, so an over-subtotal entry stays visible
 * (the save path rejects it rather than clamping). [payable] floors at 0 so the displayed Total
 * never goes negative. [percent] is `round(amount / subtotal * 100)`, 0 when subtotal is 0.
 */
data class DiscountBreakdown(
    val amount: Double,
    val percent: Int,
    val payable: Double,
)

fun discountBreakdown(subtotal: Double, discountInput: String): DiscountBreakdown {
    val amount = (discountInput.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
    val percent = if (subtotal > 0.0) (amount / subtotal * 100).roundToInt() else 0
    val payable = (subtotal - amount).coerceAtLeast(0.0)
    return DiscountBreakdown(amount = amount, percent = percent, payable = payable)
}
