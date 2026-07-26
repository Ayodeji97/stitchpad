@file:Suppress("MatchingDeclarationName")

package com.danzucker.stitchpad.feature.order.presentation.detail

/**
 * Pure payment-math helpers used by the Record Payment flow.
 *
 * Intentionally free of any Order / Firestore / coroutine dependency so the invariants
 * (clamp-to-total, non-negative balance, digits-only capping) can be unit-tested in
 * isolation without building an expect-class sharer fake for commonTest.
 */

/**
 * Cap digit-only user input so the amount cannot exceed the outstanding balance.
 * Leading zeros are trimmed, letters (never expected from a numeric keyboard) are dropped.
 * Returns an empty string when the input would evaluate to zero after trimming.
 */
internal fun capPaymentDigits(rawDigits: String, balanceRemaining: Double): String {
    val filtered = rawDigits.filter { it.isDigit() }.trimStart('0')
    val typed = filtered.toLongOrNull()
    val remainingWhole = balanceRemaining.toLong()
    val needsCapping = typed != null && balanceRemaining > 0.0 && typed > remainingWhole
    return when {
        filtered.isEmpty() -> ""
        needsCapping -> remainingWhole.toString()
        else -> filtered
    }
}

/**
 * Payment progress for the hero card's unified money block.
 *
 * [netTotal] is the price after discount, [paid] is what's been collected so far
 * (net minus the outstanding balance, clamped so rounding or an over-payment can't
 * push it negative or past the total), and [fraction] is [paid] / [netTotal] in 0f..1f.
 * A zero or negative net total (free / fully-discounted order) yields fraction 0.
 */
internal data class PaymentProgress(
    val netTotal: Double,
    val paid: Double,
    val fraction: Float,
)

internal fun paymentProgress(
    totalPrice: Double,
    balanceRemaining: Double,
    discount: Double,
): PaymentProgress {
    val netTotal = (totalPrice - discount).coerceAtLeast(0.0)
    val paid = (netTotal - balanceRemaining).coerceIn(0.0, netTotal)
    val fraction = if (netTotal <= 0.0) 0f else (paid / netTotal).toFloat().coerceIn(0f, 1f)
    return PaymentProgress(netTotal = netTotal, paid = paid, fraction = fraction)
}
