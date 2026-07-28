package com.danzucker.stitchpad.feature.collection.domain

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import com.danzucker.stitchpad.core.domain.model.StatusChange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

private const val DAY = 24L * 60L * 60L * 1000L
private const val NOW = 1_000L * DAY // arbitrary fixed "now" in ms

private fun order(
    id: String = "o",
    customerId: String = "c1",
    status: OrderStatus = OrderStatus.DELIVERED,
    totalPrice: Double = 10_000.0,
    depositPaid: Double = 0.0,
    statusHistory: List<StatusChange> = emptyList(),
    createdAt: Long = NOW,
    updatedAt: Long = NOW,
): Order = Order(
    id = id,
    userId = "u",
    customerId = customerId,
    customerName = "Ada Obi",
    items = listOf(OrderItem(id = "i-$id", garmentType = GarmentType.AGBADA, description = "", price = totalPrice)),
    status = status,
    priority = OrderPriority.NORMAL,
    statusHistory = statusHistory,
    totalPrice = totalPrice,
    payments = if (depositPaid > 0.0) {
        listOf(Payment(id = "p-$id", amount = depositPaid, method = PaymentMethod.CASH, type = PaymentType.DEPOSIT, recordedAt = createdAt))
    } else {
        emptyList()
    },
    deadline = null,
    notes = null,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun customer(id: String = "c1", phone: String = "08030000000") =
    Customer(id = id, userId = "u", name = "Ada Obi", phone = phone)

class CollectionCalculatorTest {

    private val customers = mapOf("c1" to customer())

    @Test
    fun includesReadyAndDeliveredWithBalanceOnly() {
        val orders = listOf(
            order(id = "delivered", status = OrderStatus.DELIVERED, totalPrice = 5_000.0, depositPaid = 1_000.0),
            order(id = "ready", status = OrderStatus.READY, totalPrice = 5_000.0, depositPaid = 0.0),
            order(id = "pending", status = OrderStatus.PENDING, totalPrice = 5_000.0, depositPaid = 0.0),
            order(id = "inprogress", status = OrderStatus.IN_PROGRESS, totalPrice = 5_000.0, depositPaid = 0.0),
            order(id = "paid", status = OrderStatus.DELIVERED, totalPrice = 5_000.0, depositPaid = 5_000.0),
        )
        val result = CollectionCalculator.collectibles(orders, customers, NOW)
        assertEquals(setOf("delivered", "ready"), result.map { it.orderId }.toSet())
    }

    @Test
    fun owedSincePicksEarliestReadyOrDeliveredChange() {
        val readyAt = NOW - 10 * DAY
        val deliveredAt = NOW - 3 * DAY
        val o = order(
            status = OrderStatus.DELIVERED,
            statusHistory = listOf(
                StatusChange(OrderStatus.PENDING, NOW - 20 * DAY),
                StatusChange(OrderStatus.READY, readyAt),
                StatusChange(OrderStatus.DELIVERED, deliveredAt),
            ),
        )
        val item = CollectionCalculator.collectibles(listOf(o), customers, NOW).single()
        assertEquals(readyAt, item.owedSince)
        assertEquals(10, item.daysOwed)
    }

    @Test
    fun owedSinceFallsBackToUpdatedThenCreatedWhenNoHistory() {
        val o = order(statusHistory = emptyList(), createdAt = NOW - 30 * DAY, updatedAt = NOW - 4 * DAY)
        val item = CollectionCalculator.collectibles(listOf(o), customers, NOW).single()
        assertEquals(NOW - 4 * DAY, item.owedSince)
    }

    @Test
    fun overdueAtSevenDaysNotAtSix() {
        val six = order(id = "six", statusHistory = listOf(StatusChange(OrderStatus.READY, NOW - 6 * DAY)))
        val seven = order(id = "seven", statusHistory = listOf(StatusChange(OrderStatus.READY, NOW - 7 * DAY)))
        val result = CollectionCalculator.collectibles(listOf(six, seven), customers, NOW).associateBy { it.orderId }
        assertFalse(result.getValue("six").isOverdue)
        assertTrue(result.getValue("seven").isOverdue)
    }

    @Test
    fun resolvesCustomerPhone() {
        val item = CollectionCalculator.collectibles(listOf(order()), customers, NOW).single()
        assertEquals("08030000000", item.customerPhone)
    }
}
