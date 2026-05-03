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
