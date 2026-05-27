package com.danzucker.stitchpad.feature.order.domain

import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DepositReconcilerTest {

    private fun payment(
        id: String,
        amount: Double,
        type: PaymentType,
        recordedAt: Long = 0L,
    ) = Payment(
        id = id,
        amount = amount,
        method = PaymentMethod.OTHER,
        type = type,
        recordedAt = recordedAt,
        note = null,
    )

    @Test
    fun currentDepositSum_filtersToDepositTypeOnly() {
        val loaded = listOf(
            payment("a", 500.0, PaymentType.DEPOSIT),
            payment("b", 200.0, PaymentType.PROGRESS),
            payment("c", 300.0, PaymentType.FINAL),
        )

        assertEquals(500.0, DepositReconciler.currentDepositSum(loaded))
    }

    @Test
    fun currentDepositSum_returnsZeroWhenNoDeposits() {
        val loaded = listOf(payment("a", 200.0, PaymentType.PROGRESS))

        assertEquals(0.0, DepositReconciler.currentDepositSum(loaded))
    }

    @Test
    fun currentDepositSum_returnsZeroWhenEmpty() {
        assertEquals(0.0, DepositReconciler.currentDepositSum(emptyList()))
    }

    @Test
    fun currentDepositSum_sumsMultipleDeposits() {
        val loaded = listOf(
            payment("a", 500.0, PaymentType.DEPOSIT),
            payment("b", 250.0, PaymentType.DEPOSIT),
        )

        assertEquals(750.0, DepositReconciler.currentDepositSum(loaded))
    }

    @Test
    fun nonDepositTotal_sumsProgressAndFinalOnly() {
        val loaded = listOf(
            payment("a", 500.0, PaymentType.DEPOSIT),
            payment("b", 200.0, PaymentType.PROGRESS),
            payment("c", 300.0, PaymentType.FINAL),
        )

        assertEquals(500.0, DepositReconciler.nonDepositTotal(loaded))
    }

    @Test
    fun nonDepositTotal_returnsZeroWhenOnlyDeposits() {
        val loaded = listOf(payment("a", 500.0, PaymentType.DEPOSIT))

        assertEquals(0.0, DepositReconciler.nonDepositTotal(loaded))
    }

    @Test
    fun reconcileForDeposit_replacesAllDepositPaymentsAndPreservesOthers() {
        val loaded = listOf(
            payment("a", 500.0, PaymentType.DEPOSIT, recordedAt = 100L),
            payment("b", 200.0, PaymentType.PROGRESS, recordedAt = 200L),
            payment("c", 100.0, PaymentType.DEPOSIT, recordedAt = 300L),
        )

        val result = DepositReconciler.reconcileForDeposit(
            loadedPayments = loaded,
            newDeposit = 750.0,
            recordedAt = 999L,
            newPaymentId = "new-id",
        )

        assertEquals(2, result.size)
        val progress = result.single { it.type == PaymentType.PROGRESS }
        assertEquals(200.0, progress.amount)
        assertEquals(200L, progress.recordedAt)
        val deposit = result.single { it.type == PaymentType.DEPOSIT }
        assertEquals(750.0, deposit.amount)
        assertEquals(999L, deposit.recordedAt)
        assertEquals("new-id", deposit.id)
        assertEquals(PaymentMethod.OTHER, deposit.method)
    }

    @Test
    fun reconcileForDeposit_withZeroDepositRemovesAllDepositPaymentsButKeepsOthers() {
        val loaded = listOf(
            payment("a", 500.0, PaymentType.DEPOSIT),
            payment("b", 200.0, PaymentType.PROGRESS),
            payment("c", 300.0, PaymentType.FINAL),
        )

        val result = DepositReconciler.reconcileForDeposit(
            loadedPayments = loaded,
            newDeposit = 0.0,
            recordedAt = 999L,
            newPaymentId = "unused",
        )

        assertEquals(2, result.size)
        assertTrue(result.none { it.type == PaymentType.DEPOSIT })
        assertEquals(200.0, result.single { it.type == PaymentType.PROGRESS }.amount)
        assertEquals(300.0, result.single { it.type == PaymentType.FINAL }.amount)
    }

    @Test
    fun reconcileForDeposit_withEmptyLoadedAndPositiveDepositReturnsSingleDeposit() {
        val result = DepositReconciler.reconcileForDeposit(
            loadedPayments = emptyList(),
            newDeposit = 1000.0,
            recordedAt = 999L,
            newPaymentId = "fresh",
        )

        assertEquals(1, result.size)
        val deposit = result.single()
        assertEquals(PaymentType.DEPOSIT, deposit.type)
        assertEquals(1000.0, deposit.amount)
        assertEquals(999L, deposit.recordedAt)
        assertEquals("fresh", deposit.id)
    }

    @Test
    fun reconcileForDeposit_withEmptyLoadedAndZeroDepositReturnsEmptyList() {
        val result = DepositReconciler.reconcileForDeposit(
            loadedPayments = emptyList(),
            newDeposit = 0.0,
            recordedAt = 999L,
            newPaymentId = "unused",
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun reconcileForDeposit_withNegativeDepositTreatedAsZero() {
        val loaded = listOf(payment("a", 500.0, PaymentType.DEPOSIT))

        val result = DepositReconciler.reconcileForDeposit(
            loadedPayments = loaded,
            newDeposit = -50.0,
            recordedAt = 999L,
            newPaymentId = "unused",
        )

        assertTrue(result.isEmpty())
    }
}
