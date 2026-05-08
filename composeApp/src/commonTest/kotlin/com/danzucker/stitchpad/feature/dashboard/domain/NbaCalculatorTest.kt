package com.danzucker.stitchpad.feature.dashboard.domain

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
import com.danzucker.stitchpad.feature.dashboard.presentation.model.NextBestActionType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NbaCalculatorTest {

    private fun depositPayment(amount: Double, recordedAt: Long = 0L): Payment = Payment(
        id = "test-deposit",
        amount = amount,
        method = PaymentMethod.OTHER,
        type = PaymentType.DEPOSIT,
        recordedAt = recordedAt,
    )

    private val tz = TimeZone.UTC
    private val today = LocalDate(2026, 4, 22)

    private fun millisAt(date: LocalDate, hour: Int = 12): Long =
        LocalDateTime(date, LocalTime(hour, 0)).toInstant(tz).toEpochMilliseconds()

    private fun customer(id: String = "c1", phone: String = "08011112222"): Customer =
        Customer(id = id, userId = "u", name = "Test Customer", phone = phone)

    private fun order(
        id: String = "o",
        customerId: String = "c1",
        status: OrderStatus = OrderStatus.PENDING,
        deadline: LocalDate? = null,
        balanceRemaining: Double = 0.0,
        totalPrice: Double? = null,
        depositPaid: Double = 1_000.0,
        statusHistory: List<StatusChange> = emptyList(),
        createdAt: LocalDate = today,
        updatedAt: LocalDate = today
    ): Order {
        val resolvedTotalPrice = totalPrice ?: (depositPaid + balanceRemaining)
        return Order(
            id = id,
            userId = "u",
            customerId = customerId,
            customerName = "Test Customer",
            items = listOf(
                OrderItem(id = "i-$id", garmentType = GarmentType.AGBADA, description = "", price = resolvedTotalPrice)
            ),
            status = status,
            priority = OrderPriority.NORMAL,
            statusHistory = statusHistory,
            totalPrice = resolvedTotalPrice,
            payments = if (depositPaid > 0.0) listOf(depositPayment(depositPaid)) else emptyList(),
            deadline = deadline?.let { millisAt(it) },
            notes = null,
            createdAt = millisAt(createdAt),
            updatedAt = millisAt(updatedAt),
        )
    }

    @Test
    fun emptyInputsReturnEmptyList() {
        val result = NbaCalculator.compute(emptyList(), emptyMap(), today, tz)
        assertTrue(result.isEmpty())
    }

    @Test
    fun deliveredOrdersAreSkipped() {
        val result = NbaCalculator.compute(
            orders = listOf(
                order(status = OrderStatus.DELIVERED, balanceRemaining = 5_000.0)
            ),
            customersById = mapOf("c1" to customer()),
            today = today,
            timeZone = tz
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun customersWithoutPhoneAreSkipped() {
        val result = NbaCalculator.compute(
            orders = listOf(
                order(deadline = today.minusDays(2), balanceRemaining = 5_000.0)
            ),
            customersById = mapOf("c1" to customer(phone = "")),
            today = today,
            timeZone = tz
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun ordersWithoutMatchingCustomerAreSkipped() {
        val result = NbaCalculator.compute(
            orders = listOf(
                order(customerId = "ghost", deadline = today.minusDays(2), balanceRemaining = 5_000.0)
            ),
            customersById = mapOf("c1" to customer()),
            today = today,
            timeZone = tz
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun overdueWithBalanceProducesCollectOverdue() {
        val result = NbaCalculator.compute(
            orders = listOf(
                order(deadline = today.minusDays(3), balanceRemaining = 5_000.0)
            ),
            customersById = mapOf("c1" to customer()),
            today = today,
            timeZone = tz
        )
        val nba = result.single()
        assertEquals(NextBestActionType.CollectOverdue, nba.type)
        assertEquals(5_000.0, nba.balanceAmount)
        assertEquals(3, nba.daysCount)
    }

    @Test
    fun readyWithBalanceProducesCollectOnReady() {
        val result = NbaCalculator.compute(
            orders = listOf(
                order(
                    status = OrderStatus.READY,
                    balanceRemaining = 2_500.0,
                    statusHistory = listOf(StatusChange(OrderStatus.READY, millisAt(today.minusDays(1))))
                )
            ),
            customersById = mapOf("c1" to customer()),
            today = today,
            timeZone = tz
        )
        val nba = result.single()
        assertEquals(NextBestActionType.CollectOnReady, nba.type)
        assertEquals(1, nba.daysCount)
    }

    @Test
    fun inProgressUntouchedBeyondThresholdProducesFinishStale() {
        val result = NbaCalculator.compute(
            orders = listOf(
                order(
                    status = OrderStatus.IN_PROGRESS,
                    balanceRemaining = 0.0,
                    statusHistory = listOf(
                        StatusChange(OrderStatus.IN_PROGRESS, millisAt(today.minusDays(8)))
                    )
                )
            ),
            customersById = mapOf("c1" to customer()),
            today = today,
            timeZone = tz
        )
        val nba = result.single()
        assertEquals(NextBestActionType.FinishStale, nba.type)
        assertEquals(8, nba.daysCount)
    }

    @Test
    fun readyUntouchedBeyondThresholdProducesDeliverStaleWhenNoBalance() {
        // Balance 0 means CollectOnReady doesn't apply (balanceRemaining > 0 required) so the
        // DeliverStale branch is reached.
        val result = NbaCalculator.compute(
            orders = listOf(
                order(
                    status = OrderStatus.READY,
                    balanceRemaining = 0.0,
                    statusHistory = listOf(
                        StatusChange(OrderStatus.READY, millisAt(today.minusDays(5)))
                    )
                )
            ),
            customersById = mapOf("c1" to customer()),
            today = today,
            timeZone = tz
        )
        val nba = result.single()
        assertEquals(NextBestActionType.DeliverStale, nba.type)
    }

    @Test
    fun pendingWithoutDepositProducesCollectDeposit() {
        val result = NbaCalculator.compute(
            orders = listOf(
                order(
                    status = OrderStatus.PENDING,
                    deadline = today.plusDays(20),
                    totalPrice = 12_000.0,
                    depositPaid = 0.0,
                    balanceRemaining = 12_000.0,
                    createdAt = today.minusDays(2)
                )
            ),
            customersById = mapOf("c1" to customer()),
            today = today,
            timeZone = tz
        )
        val nba = result.single()
        assertEquals(NextBestActionType.CollectDeposit, nba.type)
        assertEquals(12_000.0, nba.balanceAmount)
        assertEquals(2, nba.daysCount)
    }

    @Test
    fun pendingWithUpcomingDeadlineProducesStartSoon() {
        val result = NbaCalculator.compute(
            orders = listOf(
                order(
                    status = OrderStatus.PENDING,
                    deadline = today.plusDays(3),
                    totalPrice = 0.0,
                    depositPaid = 0.0,
                    balanceRemaining = 0.0
                )
            ),
            customersById = mapOf("c1" to customer()),
            today = today,
            timeZone = tz
        )
        val nba = result.single()
        assertEquals(NextBestActionType.StartSoon, nba.type)
        assertEquals(3, nba.daysCount)
    }

    @Test
    fun resultIsCappedAtFiveAndSortedByTypePriority() {
        // Six eligible orders across multiple types; CollectOverdue should rank first.
        val orders = listOf(
            order(id = "ovd", deadline = today.minusDays(1), balanceRemaining = 1_000.0),
            order(
                id = "ready",
                status = OrderStatus.READY,
                balanceRemaining = 1_000.0,
                statusHistory = listOf(StatusChange(OrderStatus.READY, millisAt(today)))
            ),
            order(
                id = "stale",
                status = OrderStatus.IN_PROGRESS,
                statusHistory = listOf(StatusChange(OrderStatus.IN_PROGRESS, millisAt(today.minusDays(10))))
            ),
            order(
                id = "deposit1",
                status = OrderStatus.PENDING,
                totalPrice = 5_000.0,
                depositPaid = 0.0,
                balanceRemaining = 5_000.0
            ),
            order(
                id = "deposit2",
                status = OrderStatus.PENDING,
                totalPrice = 7_000.0,
                depositPaid = 0.0,
                balanceRemaining = 7_000.0
            ),
            order(
                id = "soon",
                status = OrderStatus.PENDING,
                deadline = today.plusDays(2),
                totalPrice = 0.0,
                depositPaid = 0.0
            )
        )

        val result = NbaCalculator.compute(
            orders = orders,
            customersById = mapOf("c1" to customer()),
            today = today,
            timeZone = tz
        )

        assertEquals(5, result.size)
        assertEquals(NextBestActionType.CollectOverdue, result.first().type)
    }

    @Test
    fun startSoonOrdersTieBreakByDaysCountAscending() {
        // Two StartSoon candidates — earlier deadline must come first.
        val result = NbaCalculator.compute(
            orders = listOf(
                order(
                    id = "in5",
                    status = OrderStatus.PENDING,
                    deadline = today.plusDays(5),
                    totalPrice = 0.0,
                    depositPaid = 0.0
                ),
                order(
                    id = "in1",
                    status = OrderStatus.PENDING,
                    deadline = today.plusDays(1),
                    totalPrice = 0.0,
                    depositPaid = 0.0
                )
            ),
            customersById = mapOf("c1" to customer()),
            today = today,
            timeZone = tz
        )
        assertEquals(listOf("in1", "in5"), result.map { it.orderId })
    }

    @Test
    fun sameTypeOrdersTieBreakByBalanceDescending() {
        val result = NbaCalculator.compute(
            orders = listOf(
                order(id = "small", deadline = today.minusDays(2), balanceRemaining = 1_000.0),
                order(id = "big", deadline = today.minusDays(2), balanceRemaining = 9_000.0)
            ),
            customersById = mapOf("c1" to customer()),
            today = today,
            timeZone = tz
        )
        assertEquals(listOf("big", "small"), result.map { it.orderId })
    }

    private fun LocalDate.minusDays(n: Long): LocalDate =
        LocalDate.fromEpochDays((toEpochDays() - n).toInt())

    private fun LocalDate.plusDays(n: Long): LocalDate =
        LocalDate.fromEpochDays((toEpochDays() + n).toInt())
}
