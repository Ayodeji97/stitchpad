package com.danzucker.stitchpad.feature.dashboard.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class PipelinePaymentStatusTest {
    @Test fun nothingCollected_isDepositDue() =
        assertEquals(PipelinePaymentStatus.DepositDue, pipelinePaymentStatusOf(0.0, 40000.0))
    @Test fun partial_isDepositPaid() =
        assertEquals(PipelinePaymentStatus.DepositPaid, pipelinePaymentStatusOf(20000.0, 40000.0))
    @Test fun fullyPaid_isPaid() =
        assertEquals(PipelinePaymentStatus.Paid, pipelinePaymentStatusOf(40000.0, 40000.0))
    @Test fun overpaid_isPaid() =
        assertEquals(PipelinePaymentStatus.Paid, pipelinePaymentStatusOf(50000.0, 40000.0))
    @Test fun zeroTotal_isPaid() =
        assertEquals(PipelinePaymentStatus.Paid, pipelinePaymentStatusOf(0.0, 0.0))
}
