package com.danzucker.stitchpad.feature.dashboard.domain

enum class PipelinePaymentStatus { DepositDue, DepositPaid, Paid }

/**
 * Derives the pipeline footer's payment chip from collected vs payable amount.
 * - payableTotal <= 0  -> Paid (nothing to collect)
 * - nothing collected  -> DepositDue (the actionable state)
 * - collected >= total -> Paid
 * - otherwise          -> DepositPaid (partial)
 */
fun pipelinePaymentStatusOf(depositPaid: Double, payableTotal: Double): PipelinePaymentStatus =
    when {
        payableTotal <= 0.0 -> PipelinePaymentStatus.Paid
        depositPaid <= 0.0 -> PipelinePaymentStatus.DepositDue
        depositPaid >= payableTotal -> PipelinePaymentStatus.Paid
        else -> PipelinePaymentStatus.DepositPaid
    }
