package com.danzucker.stitchpad.feature.dashboard.domain

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReconnectCalculatorTest {

    private val tz = TimeZone.UTC
    private val today = LocalDate(2026, 4, 22)

    private fun millisAt(date: LocalDate, hour: Int = 12): Long =
        LocalDateTime(date, LocalTime(hour, 0)).toInstant(tz).toEpochMilliseconds()

    private fun customer(id: String, phone: String = "08011112222", name: String = "C-$id"): Customer =
        Customer(id = id, userId = "u", name = name, phone = phone)

    private fun deliveredOrder(customerId: String, updatedAt: LocalDate): Order =
        Order(
            id = "o-$customerId-${updatedAt.toEpochDays()}",
            userId = "u",
            customerId = customerId,
            customerName = "Test",
            items = listOf(
                OrderItem(id = "i", garmentType = GarmentType.AGBADA, description = "", price = 0.0)
            ),
            status = OrderStatus.DELIVERED,
            priority = OrderPriority.NORMAL,
            statusHistory = emptyList(),
            totalPrice = 0.0,
            depositPaid = 0.0,
            balanceRemaining = 0.0,
            deadline = null,
            notes = null,
            createdAt = millisAt(updatedAt),
            updatedAt = millisAt(updatedAt)
        )

    private fun activeOrder(customerId: String, updatedAt: LocalDate): Order =
        deliveredOrder(customerId, updatedAt).copy(status = OrderStatus.IN_PROGRESS)

    @Test
    fun emptyCustomersReturnsEmptyList() {
        val result = ReconnectCalculator.compute(emptyList(), emptyList(), today, tz)
        assertTrue(result.isEmpty())
    }

    @Test
    fun customersWithActiveOrdersAreExcluded() {
        val result = ReconnectCalculator.compute(
            orders = listOf(activeOrder("c1", today)),
            customers = listOf(customer("c1")),
            today = today,
            timeZone = tz
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun customersWithoutPhoneAreExcluded() {
        val result = ReconnectCalculator.compute(
            orders = emptyList(),
            customers = listOf(customer("c1", phone = "")),
            today = today,
            timeZone = tz
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun newCustomerWithNoOrderHistoryAlwaysPasses() {
        val result = ReconnectCalculator.compute(
            orders = emptyList(),
            customers = listOf(customer("new")),
            today = today,
            timeZone = tz
        )
        val candidate = result.single()
        assertEquals("new", candidate.customerId)
        assertEquals(0, candidate.daysSinceLastInteraction)
        assertEquals(false, candidate.hasOrderHistory)
    }

    @Test
    fun customerInactiveAtLeastFourteenDaysIsIncluded() {
        val result = ReconnectCalculator.compute(
            orders = listOf(deliveredOrder("c1", today.minusDays(14))),
            customers = listOf(customer("c1")),
            today = today,
            timeZone = tz
        )
        val candidate = result.single()
        assertEquals(14, candidate.daysSinceLastInteraction)
        assertEquals(true, candidate.hasOrderHistory)
    }

    @Test
    fun customerInactiveLessThanFourteenDaysIsExcluded() {
        val result = ReconnectCalculator.compute(
            orders = listOf(deliveredOrder("c1", today.minusDays(13))),
            customers = listOf(customer("c1")),
            today = today,
            timeZone = tz
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun resultIsSortedByDaysSinceDescending() {
        val result = ReconnectCalculator.compute(
            orders = listOf(
                deliveredOrder("c1", today.minusDays(20)),
                deliveredOrder("c2", today.minusDays(60)),
                deliveredOrder("c3", today.minusDays(30))
            ),
            customers = listOf(customer("c1"), customer("c2"), customer("c3")),
            today = today,
            timeZone = tz
        )
        assertEquals(listOf("c2", "c3", "c1"), result.map { it.customerId })
    }

    @Test
    fun resultIsCappedAtFiveCandidates() {
        val customers = (1..7).map { customer(id = "c$it") }
        val orders = customers.mapIndexed { index, c ->
            deliveredOrder(c.id, today.minusDays((20 + index).toLong()))
        }

        val result = ReconnectCalculator.compute(orders, customers, today, tz)

        assertEquals(5, result.size)
    }

    @Test
    fun customerActiveOrderTakesPrecedenceOverPastDeliveredHistory() {
        // c1 has a delivered order from long ago (eligible) AND an active in-progress order (excludes).
        val result = ReconnectCalculator.compute(
            orders = listOf(
                deliveredOrder("c1", today.minusDays(60)),
                activeOrder("c1", today)
            ),
            customers = listOf(customer("c1")),
            today = today,
            timeZone = tz
        )
        assertTrue(result.isEmpty())
    }

    private fun LocalDate.minusDays(n: Long): LocalDate =
        LocalDate.fromEpochDays((toEpochDays() - n).toInt())
}
