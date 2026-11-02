package com.danzucker.stitchpad.feature.order.presentation.list

import com.danzucker.stitchpad.core.domain.model.Currency
import com.danzucker.stitchpad.core.sharing.formatMoney
import kotlin.math.roundToLong

sealed interface PaymentDisplay {
    data object Paid : PaymentDisplay
    data class Partial(val amountPaid: Double) : PaymentDisplay {
        fun formatAbbreviated(currency: Currency = Currency.NGN): String {
            // Round, don't truncate: ₦1,999.90 deposit should read ₦2000 or ₦2k,
            // not ₦1999/₦1k (which would under-represent what the customer paid).
            val rounded = amountPaid.roundToLong()
            return if (rounded >= 1_000) {
                formatMoney((rounded / 1_000).toDouble(), currency) + "k"
            } else {
                formatMoney(rounded.toDouble(), currency)
            }
        }
    }
    data object Unpaid : PaymentDisplay
}

fun formatPaymentStatus(depositPaid: Double, amountOwed: Double): PaymentDisplay = when {
    amountOwed <= 0.0 -> PaymentDisplay.Paid
    depositPaid >= amountOwed -> PaymentDisplay.Paid
    depositPaid > 0.0 -> PaymentDisplay.Partial(amountPaid = depositPaid)
    else -> PaymentDisplay.Unpaid
}
