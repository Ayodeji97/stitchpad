@file:Suppress("MatchingDeclarationName")

package com.danzucker.stitchpad.feature.order.presentation.detail

import com.danzucker.stitchpad.core.domain.model.Currency
import com.danzucker.stitchpad.core.presentation.capMoneyInput
import com.danzucker.stitchpad.core.presentation.moneyAmountAtPrecision
import com.danzucker.stitchpad.core.presentation.moneyInputAfterEdit

/**
 * Pure payment-math helpers used by the Record Payment flow.
 *
 * Intentionally free of any Order / Firestore / coroutine dependency so the invariants
 * (clamp-to-total, non-negative balance, digits-only capping) can be unit-tested in
 * isolation without building an expect-class sharer fake for commonTest.
 */

/** Cap major-unit edits without losing the record currency's supported fractional digits. */
internal fun capPaymentDigits(
    rawDigits: String,
    balanceRemaining: Double,
    currency: Currency = Currency.NGN,
    previous: String = "",
): String {
    val accepted = moneyInputAfterEdit(rawDigits, previous, currency)
    val trimmed = accepted.trimStart('0')
    val canonical = if (trimmed.startsWith('.')) "0$trimmed" else trimmed
    return capMoneyInput(canonical, balanceRemaining, currency)
}

/** Accept representable payments while ignoring only binary floating-point noise in balances. */
internal fun paymentAmountAtPrecision(amount: Double, currency: Currency): Double? =
    moneyAmountAtPrecision(amount, currency)

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
