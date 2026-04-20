package com.danzucker.stitchpad.feature.order.presentation.list

sealed interface PaymentDisplay {
    data object Paid : PaymentDisplay
    data class Partial(val amountPaid: Double) : PaymentDisplay {
        fun formatAbbreviated(): String {
            val long = amountPaid.toLong()
            return if (long >= 1_000) "\u20A6${long / 1_000}k" else "\u20A6$long"
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
