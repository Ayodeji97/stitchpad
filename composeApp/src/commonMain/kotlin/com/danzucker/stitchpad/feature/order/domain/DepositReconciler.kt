package com.danzucker.stitchpad.feature.order.domain

import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType

/**
 * Pure reconciliation rules for the "Deposit paid" form field.
 *
 * The form treats deposit as a single editable value, but the underlying
 * model keeps a typed [Payment] list. Editing the form value replaces all
 * DEPOSIT entries with at most one fresh DEPOSIT; PROGRESS and FINAL
 * entries are preserved untouched.
 */
object DepositReconciler {

    /** Sum of just the DEPOSIT-typed payments — what the form field should display. */
    fun currentDepositSum(loadedPayments: List<Payment>): Double =
        loadedPayments.filter { it.type == PaymentType.DEPOSIT }.sumOf { it.amount }

    /** Sum of PROGRESS + FINAL payments — surfaced in the reconciliation dialog. */
    fun nonDepositTotal(loadedPayments: List<Payment>): Double =
        loadedPayments.filter { it.type != PaymentType.DEPOSIT }.sumOf { it.amount }

    /**
     * Returns the payments list to persist after the user has set the form's
     * "Deposit paid" value to [newDeposit].
     *
     * All existing DEPOSIT payments are dropped; if [newDeposit] is positive a
     * single fresh DEPOSIT is appended. Non-DEPOSIT payments pass through unchanged.
     */
    fun reconcileForDeposit(
        loadedPayments: List<Payment>,
        newDeposit: Double,
        recordedAt: Long,
        newPaymentId: String,
    ): List<Payment> {
        val nonDeposit = loadedPayments.filter { it.type != PaymentType.DEPOSIT }
        val freshDeposit = if (newDeposit > 0.0) {
            listOf(
                Payment(
                    id = newPaymentId,
                    amount = newDeposit,
                    method = PaymentMethod.OTHER,
                    type = PaymentType.DEPOSIT,
                    recordedAt = recordedAt,
                    note = null,
                ),
            )
        } else {
            emptyList()
        }
        return nonDeposit + freshDeposit
    }
}
