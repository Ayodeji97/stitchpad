@file:Suppress("MatchingDeclarationName")

package com.danzucker.stitchpad.feature.order.presentation.detail

/**
 * Pure payment-math helpers used by the Record Payment flow.
 *
 * Intentionally free of any Order / Firestore / coroutine dependency so the invariants
 * (clamp-to-total, non-negative balance, digits-only capping) can be unit-tested in
 * isolation without building an expect-class sharer fake for commonTest.
 */

internal data class RecordedPayment(
    val newDeposit: Double,
    val newBalance: Double,
)

/**
 * Increment [currentDeposit] by [amountJustPaid], clamped to [totalPrice]. Returns the
 * new deposit and derived balance (never negative). A tailor who over-taps "mark paid in
 * full" or types an amount larger than the balance is recorded as fully paid, not owed money.
 */
internal fun computeRecordedPayment(
    currentDeposit: Double,
    totalPrice: Double,
    amountJustPaid: Double,
): RecordedPayment {
    val newDeposit = (currentDeposit + amountJustPaid).coerceAtMost(totalPrice)
    val newBalance = (totalPrice - newDeposit).coerceAtLeast(0.0)
    return RecordedPayment(newDeposit = newDeposit, newBalance = newBalance)
}

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
