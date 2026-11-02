package com.danzucker.stitchpad.feature.reports.domain

import com.danzucker.stitchpad.core.domain.model.Currency
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import com.danzucker.stitchpad.feature.reports.domain.model.ReportsPeriod
import com.danzucker.stitchpad.feature.reports.domain.model.CustomerBadge
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CurrencyReportsCalculatorTest {
    private val today = LocalDate(2026, 4, 22)
    private val now = LocalDateTime(2026, 4, 22, 12, 0).toInstant(TimeZone.UTC).toEpochMilliseconds()
    private val previous = LocalDateTime(2026, 4, 15, 12, 0).toInstant(TimeZone.UTC).toEpochMilliseconds()
    private val customer = Customer("c", "u", "Ada", "+233244123456", null, null, 0L)

    private fun order(id: String, currency: Currency, total: Double, paid: Double, date: Long = now) = Order(
        id = id,
        userId = "u",
        customerId = customer.id,
        customerName = customer.name,
        items = emptyList(),
        status = OrderStatus.READY,
        priority = OrderPriority.NORMAL,
        statusHistory = emptyList(),
        totalPrice = total,
        payments = listOf(Payment(id, paid, PaymentMethod.CASH, PaymentType.DEPOSIT, date)),
        deadline = null,
        notes = null,
        currency = currency,
        createdAt = date,
        updatedAt = date,
    )

    @Test
    fun historicalAndCurrentCurrenciesNeverCombineInTotalsSparklinesOrCustomerDebt() {
        val groups = CurrencyReportsCalculator.compute(
            listOf(
                order("ng", Currency.NGN, 1000.0, 100.0),
                order("gh", Currency.GHS, 200.0, 50.0),
                order("old-gh", Currency.GHS, 80.0, 40.0, previous),
            ),
            listOf(customer), ReportsPeriod.WEEK, today, TimeZone.UTC,
        ).associateBy { it.currency }
        val nigeria = groups.getValue(Currency.NGN)
        val ghana = groups.getValue(Currency.GHS)
        assertEquals(1000.0, nigeria.kpiSummary.revenue.current)
        assertEquals(200.0, ghana.kpiSummary.revenue.current)
        assertEquals(80.0, ghana.kpiSummary.revenue.previous)
        assertEquals(200.0, ghana.kpiSummary.revenue.sparkline.last())
        assertEquals(900.0, nigeria.debtors.items.single().totalOwed)
        assertEquals(190.0, ghana.debtors.items.single().totalOwed)
        assertEquals(Currency.GHS, ghana.debtors.items.single().currency)
        assertEquals(50.0, ghana.topCustomers.items.single().totalCollected)
        val counts = CurrencyReportsCalculator.orderCount(groups.values.toList())!!
        assertEquals(2.0, counts.current)
        assertEquals(1.0, counts.previous)
        assertEquals(2.0, counts.sparkline.last())
    }

    @Test
    fun globalOrderCountBadgesArePreservedWithoutApplyingNairaSpendThresholdToForeignAmounts() {
        val oneForeignOrder = CurrencyReportsCalculator.compute(
            listOf(order("gh", Currency.GHS, 600000.0, 600000.0)),
            listOf(customer), ReportsPeriod.WEEK, today, TimeZone.UTC,
        )
        assertEquals(CustomerBadge.NONE, oneForeignOrder.single().topCustomers.items.single().badge)
        val fiveAcrossMarkets = CurrencyReportsCalculator.compute(
            listOf(order("gh", Currency.GHS, 200.0, 200.0)) +
                (1..4).map { order("ng$it", Currency.NGN, 1000.0, 1000.0) },
            listOf(customer), ReportsPeriod.WEEK, today, TimeZone.UTC,
        )
        fiveAcrossMarkets.forEach { assertEquals(CustomerBadge.VIP, it.topCustomers.items.single().badge) }
    }

    @Test
    fun singleCurrencyKeepsExistingTotalsAndEmptyDataHasNoInventedCurrency() {
        val groups = CurrencyReportsCalculator.compute(
            listOf(order("gh", Currency.GHS, 200.0, 50.0)),
            listOf(customer), ReportsPeriod.WEEK, today, TimeZone.UTC,
        )
        assertEquals(Currency.GHS, groups.single().currency)
        assertEquals(150.0, groups.single().kpiSummary.outstanding.current)
        assertEquals(emptyList(), CurrencyReportsCalculator.compute(emptyList(), emptyList(), ReportsPeriod.WEEK, today, TimeZone.UTC))
        assertNull(CurrencyReportsCalculator.orderCount(emptyList()))
    }
}
