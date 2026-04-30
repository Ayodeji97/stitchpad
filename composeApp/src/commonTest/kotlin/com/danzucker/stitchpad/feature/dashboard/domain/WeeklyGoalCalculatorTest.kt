package com.danzucker.stitchpad.feature.dashboard.domain

import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.feature.goals.domain.model.WeeklyGoal
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WeeklyGoalCalculatorTest {

    private val tz = TimeZone.UTC
    // 2026-04-22 is a Wednesday — week start (Mon) is 2026-04-20.
    private val today = LocalDate(2026, 4, 22)

    private fun millisAt(date: LocalDate, hour: Int = 12): Long =
        LocalDateTime(date, LocalTime(hour, 0)).toInstant(tz).toEpochMilliseconds()

    private fun order(
        updatedAt: LocalDate,
        totalPrice: Double = 0.0,
        balanceRemaining: Double = 0.0
    ): Order = Order(
        id = "o-${updatedAt.toEpochDays()}",
        userId = "u",
        customerId = "c1",
        customerName = "Test",
        items = listOf(
            OrderItem(id = "i", garmentType = GarmentType.AGBADA, description = "", price = totalPrice)
        ),
        status = OrderStatus.PENDING,
        priority = OrderPriority.NORMAL,
        statusHistory = emptyList(),
        totalPrice = totalPrice,
        depositPaid = totalPrice - balanceRemaining,
        balanceRemaining = balanceRemaining,
        deadline = null,
        notes = null,
        createdAt = millisAt(updatedAt),
        updatedAt = millisAt(updatedAt)
    )

    @Test
    fun nullGoalReturnsNull() {
        val result = WeeklyGoalCalculator.compute(emptyList(), today, goal = null, timeZone = tz)
        assertNull(result)
    }

    @Test
    fun goalWithNoOrdersReturnsZeroCollected() {
        val result = WeeklyGoalCalculator.compute(
            orders = emptyList(),
            today = today,
            goal = WeeklyGoal(targetAmount = 100_000.0, updatedAt = 0L),
            timeZone = tz
        )!!
        assertEquals(0.0, result.collectedAmount)
        assertEquals(100_000.0, result.targetAmount)
    }

    @Test
    fun ordersBeforeWeekStartAreExcluded() {
        // Last week's Sunday (2026-04-19) is before this Monday's start.
        val result = WeeklyGoalCalculator.compute(
            orders = listOf(
                order(updatedAt = today.minusDays(3), totalPrice = 50_000.0)
            ),
            today = today,
            goal = WeeklyGoal(targetAmount = 100_000.0, updatedAt = 0L),
            timeZone = tz
        )!!
        assertEquals(0.0, result.collectedAmount)
    }

    @Test
    fun collectedAmountSumsPaidPortionOfThisWeeksOrders() {
        val result = WeeklyGoalCalculator.compute(
            orders = listOf(
                order(updatedAt = today.minusDays(2), totalPrice = 30_000.0, balanceRemaining = 10_000.0),
                order(updatedAt = today, totalPrice = 50_000.0, balanceRemaining = 0.0)
            ),
            today = today,
            goal = WeeklyGoal(targetAmount = 100_000.0, updatedAt = 0L),
            timeZone = tz
        )!!
        // (30_000 - 10_000) + (50_000 - 0) = 70_000
        assertEquals(70_000.0, result.collectedAmount)
    }

    @Test
    fun negativePaidPortionsClampToZero() {
        // Pathological: balance > totalPrice (shouldn't happen but the formula must clamp).
        val result = WeeklyGoalCalculator.compute(
            orders = listOf(
                order(updatedAt = today, totalPrice = 1_000.0, balanceRemaining = 5_000.0)
            ),
            today = today,
            goal = WeeklyGoal(targetAmount = 100_000.0, updatedAt = 0L),
            timeZone = tz
        )!!
        assertEquals(0.0, result.collectedAmount)
    }

    @Test
    fun daysLeftIsSixOnMondayAndZeroOnSunday() {
        val monday = LocalDate(2026, 4, 20)
        val sunday = LocalDate(2026, 4, 26)
        val goal = WeeklyGoal(targetAmount = 100_000.0, updatedAt = 0L)

        val mondayResult = WeeklyGoalCalculator.compute(emptyList(), monday, goal, tz)!!
        val sundayResult = WeeklyGoalCalculator.compute(emptyList(), sunday, goal, tz)!!

        assertEquals(6, mondayResult.daysLeft)
        assertEquals(0, sundayResult.daysLeft)
    }

    @Test
    fun progressPercentDerivesFromCollectedOverTarget() {
        val result = WeeklyGoalCalculator.compute(
            orders = listOf(
                order(updatedAt = today, totalPrice = 25_000.0, balanceRemaining = 0.0)
            ),
            today = today,
            goal = WeeklyGoal(targetAmount = 100_000.0, updatedAt = 0L),
            timeZone = tz
        )!!
        assertEquals(0.25f, result.progressPercent)
    }

    private fun LocalDate.minusDays(n: Long): LocalDate =
        LocalDate.fromEpochDays((toEpochDays() - n).toInt())
}
