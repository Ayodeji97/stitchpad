package com.danzucker.stitchpad.feature.dashboard.domain

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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BucketCalculatorTest {

    private val tz = TimeZone.UTC
    private val today = LocalDate(2026, 4, 22)

    private fun millisAt(date: LocalDate, hour: Int = 12): Long =
        LocalDateTime(date, LocalTime(hour, 0)).toInstant(tz).toEpochMilliseconds()

    private fun order(
        id: String = "o",
        status: OrderStatus = OrderStatus.PENDING,
        deadline: LocalDate? = null,
        balanceRemaining: Double = 0.0,
        totalPrice: Double = 0.0,
        garment: GarmentType = GarmentType.AGBADA,
        customerName: String = "Test"
    ): Order = Order(
        id = id,
        userId = "u",
        customerId = "c1",
        customerName = customerName,
        items = listOf(
            OrderItem(id = "i-$id", garmentType = garment, description = "", price = totalPrice)
        ),
        status = status,
        priority = OrderPriority.NORMAL,
        statusHistory = emptyList(),
        totalPrice = totalPrice,
        depositPaid = totalPrice - balanceRemaining,
        balanceRemaining = balanceRemaining,
        deadline = deadline?.let { millisAt(it) },
        notes = null,
        createdAt = millisAt(today),
        updatedAt = millisAt(today)
    )

    @Test
    fun emptyOrdersProducesEmptyBuckets() {
        val buckets = BucketCalculator.compute(emptyList(), today, tz)

        assertTrue(buckets.overdue.isEmpty())
        assertTrue(buckets.dueToday.isEmpty())
        assertTrue(buckets.ready.isEmpty())
        assertTrue(buckets.pipelineInProgress.isEmpty())
        assertTrue(buckets.pipelinePending.isEmpty())
        assertEquals(0, buckets.outstandingOrderCount)
        assertEquals(0.0, buckets.outstandingAmount)
    }

    @Test
    fun overdueIncludesActiveOrdersWithDeadlineBeforeToday() {
        val orders = listOf(
            order(id = "yesterday", deadline = today.minusDays(1)),
            order(id = "lastWeek", deadline = today.minusDays(7))
        )

        val buckets = BucketCalculator.compute(orders, today, tz)

        assertEquals(2, buckets.overdue.size)
        assertEquals(listOf("lastWeek", "yesterday"), buckets.overdue.map { it.orderId })
    }

    @Test
    fun overdueIsSortedByDeadlineAscending() {
        val orders = listOf(
            order(id = "newest", deadline = today.minusDays(1)),
            order(id = "oldest", deadline = today.minusDays(10)),
            order(id = "middle", deadline = today.minusDays(5))
        )

        val buckets = BucketCalculator.compute(orders, today, tz)

        assertEquals(listOf("oldest", "middle", "newest"), buckets.overdue.map { it.orderId })
    }

    @Test
    fun deliveredOrdersExcludedFromOverdue() {
        val orders = listOf(
            order(id = "delivered", status = OrderStatus.DELIVERED, deadline = today.minusDays(2)),
            order(id = "pending", status = OrderStatus.PENDING, deadline = today.minusDays(1))
        )

        val buckets = BucketCalculator.compute(orders, today, tz)

        assertEquals(listOf("pending"), buckets.overdue.map { it.orderId })
    }

    @Test
    fun dueTodayCapturesActiveOrdersWithDeadlineEqualToday() {
        val orders = listOf(
            order(id = "today", deadline = today),
            order(id = "tomorrow", deadline = today.plusDays(1)),
            order(id = "delivered", status = OrderStatus.DELIVERED, deadline = today)
        )

        val buckets = BucketCalculator.compute(orders, today, tz)

        assertEquals(listOf("today"), buckets.dueToday.map { it.orderId })
    }

    @Test
    fun readyIncludesOrdersInReadyStatusRegardlessOfDeadline() {
        val orders = listOf(
            order(id = "readyOverdue", status = OrderStatus.READY, deadline = today.minusDays(3)),
            order(id = "readyFuture", status = OrderStatus.READY, deadline = today.plusDays(2)),
            order(id = "pending", status = OrderStatus.PENDING, deadline = today.plusDays(2))
        )

        val buckets = BucketCalculator.compute(orders, today, tz)

        assertEquals(2, buckets.ready.size)
        assertTrue(buckets.ready.any { it.orderId == "readyOverdue" })
        assertTrue(buckets.ready.any { it.orderId == "readyFuture" })
    }

    @Test
    fun pipelineExcludesTriageOrders() {
        // Triage = overdue + dueToday + ready. Pipeline must not include any of these.
        val orders = listOf(
            order(id = "overdue", deadline = today.minusDays(1), status = OrderStatus.IN_PROGRESS),
            order(id = "dueToday", deadline = today, status = OrderStatus.IN_PROGRESS),
            order(id = "ready", status = OrderStatus.READY),
            order(id = "future", deadline = today.plusDays(5), status = OrderStatus.IN_PROGRESS)
        )

        val buckets = BucketCalculator.compute(orders, today, tz)

        assertEquals(listOf("future"), buckets.pipelineInProgress.map { it.orderId })
        assertEquals(1, buckets.pipelineInProgressTotal)
    }

    @Test
    fun pipelinePreviewIsCappedAtThreeButTotalReflectsFullCount() {
        val orders = (1..7).map {
            order(id = "p$it", deadline = today.plusDays(it.toLong()), status = OrderStatus.PENDING)
        }

        val buckets = BucketCalculator.compute(orders, today, tz)

        assertEquals(3, buckets.pipelinePending.size)
        assertEquals(7, buckets.pipelinePendingTotal)
        assertEquals(listOf("p1", "p2", "p3"), buckets.pipelinePending.map { it.orderId })
    }

    @Test
    fun pipelineSplitsByInProgressVsPendingStatus() {
        val orders = listOf(
            order(id = "ip1", deadline = today.plusDays(2), status = OrderStatus.IN_PROGRESS),
            order(id = "ip2", deadline = today.plusDays(4), status = OrderStatus.IN_PROGRESS),
            order(id = "p1", deadline = today.plusDays(3), status = OrderStatus.PENDING)
        )

        val buckets = BucketCalculator.compute(orders, today, tz)

        assertEquals(2, buckets.pipelineInProgressTotal)
        assertEquals(1, buckets.pipelinePendingTotal)
    }

    @Test
    fun outstandingSumsBalanceRemainingAcrossActiveOrdersOnly() {
        val orders = listOf(
            order(id = "active1", status = OrderStatus.IN_PROGRESS, balanceRemaining = 100.0),
            order(id = "active2", status = OrderStatus.READY, balanceRemaining = 250.0),
            order(id = "delivered", status = OrderStatus.DELIVERED, balanceRemaining = 999.0),
            order(id = "paid", status = OrderStatus.PENDING, balanceRemaining = 0.0)
        )

        val buckets = BucketCalculator.compute(orders, today, tz)

        assertEquals(2, buckets.outstandingOrderCount)
        assertEquals(350.0, buckets.outstandingAmount)
    }

    @Test
    fun overdueRowExposesPositiveDaysLate() {
        val orders = listOf(order(id = "o1", deadline = today.minusDays(3)))

        val buckets = BucketCalculator.compute(orders, today, tz)

        assertEquals(3, buckets.overdue.single().daysLate)
        assertNull(buckets.overdue.single().daysUntilDeadline)
    }

    @Test
    fun pipelineRowExposesPositiveDaysUntilDeadline() {
        val orders = listOf(
            order(id = "o1", deadline = today.plusDays(5), status = OrderStatus.IN_PROGRESS)
        )

        val buckets = BucketCalculator.compute(orders, today, tz)

        assertEquals(5, buckets.pipelineInProgress.single().daysUntilDeadline)
        assertNull(buckets.pipelineInProgress.single().daysLate)
    }

    private fun LocalDate.minusDays(n: Long): LocalDate =
        LocalDate.fromEpochDays((toEpochDays() - n).toInt())

    private fun LocalDate.plusDays(n: Long): LocalDate =
        LocalDate.fromEpochDays((toEpochDays() + n).toInt())
}
