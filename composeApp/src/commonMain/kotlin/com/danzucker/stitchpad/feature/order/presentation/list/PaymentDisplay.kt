package com.danzucker.stitchpad.feature.order.presentation.list

import kotlin.math.roundToLong

sealed interface PaymentDisplay {
    data object Paid : PaymentDisplay
    data class Partial(val amountPaid: Double) : PaymentDisplay {
        fun formatAbbreviated(): String {
            // Round, don't truncate: ₦1,999.90 deposit should read ₦2000 or ₦2k,
            // not ₦1999/₦1k (which would under-represent what the customer paid).
            val rounded = amountPaid.roundToLong()
            return if (rounded >= 1_000) "\u20A6${rounded / 1_000}k" else "\u20A6$rounded"
        }
    }
    data object Unpaid : PaymentDisplay
}

fun formatPaymentStatus(depositPaid: Double, totalPrice: Double): PaymentDisplay = when {
    totalPrice <= 0.0 -> PaymentDisplay.Paid
    depositPaid >= totalPrice -> PaymentDisplay.Paid
    depositPaid > 0.0 -> PaymentDisplay.Partial(amountPaid = depositPaid)
    else -> PaymentDisplay.Unpaid
}
