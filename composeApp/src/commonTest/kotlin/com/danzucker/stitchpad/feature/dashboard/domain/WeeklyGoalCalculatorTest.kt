package com.danzucker.stitchpad.feature.dashboard.domain

import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import com.danzucker.stitchpad.feature.goals.domain.model.WeeklyGoal
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Cycles are anchored to the goal's `updatedAt` (creation/last-edit day),
 * not the ISO calendar week. A goal set on Saturday runs Sat-Fri, then
 * rolls into the next Sat-Fri cycle automatically.
 */
class WeeklyGoalCalculatorTest {

    private fun depositPayment(amount: Double, recordedAt: Long = 0L): Payment = Payment(
        id = "test-deposit",
        amount = amount,
        method = PaymentMethod.OTHER,
        type = PaymentType.DEPOSIT,
        recordedAt = recordedAt,
    )

    private val tz = TimeZone.UTC

    /** Goal was set on Saturday 2026-05-02 (mirrors the bug report context). */
    private val goalCreated = LocalDate(2026, 5, 2)
    private val goalCreatedMillis = millisAt(goalCreated)

    private fun millisAt(date: LocalDate, hour: Int = 12): Long =
        LocalDateTime(date, LocalTime(hour, 0)).toInstant(tz).toEpochMilliseconds()

    private fun goal(targetAmount: Double = 100_000.0): WeeklyGoal =
        WeeklyGoal(targetAmount = targetAmount, updatedAt = goalCreatedMillis)

    private fun order(
        updatedAt: LocalDate,
        totalPrice: Double = 0.0,
        balanceRemaining: Double = 0.0
    ): Order {
        val depositAmount = (totalPrice - balanceRemaining).coerceAtLeast(0.0)
        return Order(
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
            payments = if (depositAmount > 0.0) listOf(depositPayment(depositAmount)) else emptyList(),
            deadline = null,
            notes = null,
            createdAt = millisAt(updatedAt),
            updatedAt = millisAt(updatedAt),
        )
    }

    @Test
    fun nullGoalReturnsNull() {
        val result = WeeklyGoalCalculator.compute(emptyList(), goalCreated, goal = null, timeZone = tz)
        assertNull(result)
    }

    @Test
    fun goalWithNoOrdersReturnsZeroCollected() {
        val result = WeeklyGoalCalculator.compute(
            orders = emptyList(),
            today = goalCreated,
            goal = goal(),
            timeZone = tz
        )!!
        assertEquals(0.0, result.collectedAmount)
        assertEquals(100_000.0, result.targetAmount)
    }

    @Test
    fun ordersBeforeCycleStartAreExcluded() {
        // Day before the goal was created — outside the first cycle.
        val result = WeeklyGoalCalculator.compute(
            orders = listOf(
                order(updatedAt = goalCreated.minusDays(1), totalPrice = 50_000.0)
            ),
            today = goalCreated,
            goal = goal(),
            timeZone = tz
        )!!
        assertEquals(0.0, result.collectedAmount)
    }

    @Test
    fun collectedAmountSumsPaidPortionOfCurrentCycle() {
        val result = WeeklyGoalCalculator.compute(
            orders = listOf(
                order(updatedAt = goalCreated, totalPrice = 30_000.0, balanceRemaining = 10_000.0),
                order(updatedAt = goalCreated.plusDays(2), totalPrice = 50_000.0, balanceRemaining = 0.0)
            ),
            today = goalCreated.plusDays(3),
            goal = goal(),
            timeZone = tz
        )!!
        // (30_000 - 10_000) + (50_000 - 0) = 70_000
        assertEquals(70_000.0, result.collectedAmount)
    }

    @Test
    fun negativePaidPortionsClampToZero() {
        val result = WeeklyGoalCalculator.compute(
            orders = listOf(
                order(updatedAt = goalCreated, totalPrice = 1_000.0, balanceRemaining = 5_000.0)
            ),
            today = goalCreated,
            goal = goal(),
            timeZone = tz
        )!!
        assertEquals(0.0, result.collectedAmount)
    }

    /**
     * On the day a goal is created the user should still see a full window
     * ahead — six days remaining after today. This is the regression the
     * "1 days left on Saturday after just creating the goal" bug exposed.
     */
    @Test
    fun daysLeftIsSixOnCycleStartAndZeroOnCycleEnd() {
        val cycleEnd = goalCreated.plusDays(6) // 2026-05-08 (Friday)

        val onStart = WeeklyGoalCalculator.compute(emptyList(), goalCreated, goal(), tz)!!
        val onEnd = WeeklyGoalCalculator.compute(emptyList(), cycleEnd, goal(), tz)!!

        assertEquals(6, onStart.daysLeft)
        assertEquals(0, onEnd.daysLeft)
    }

    /**
     * Past the first cycle, the calculator rolls forward to the next 7-day
     * window. A user who set a goal three weeks ago and opens the app today
     * gets a fresh "6 days left" today, not "0 days left forever".
     */
    @Test
    fun cycleRollsOverAfterSevenDays() {
        // 8 days after creation = day 1 of cycle 2.
        val nextCycleDay1 = goalCreated.plusDays(8)
        val result = WeeklyGoalCalculator.compute(
            orders = listOf(
                // Cycle 1 order — must NOT be counted.
                order(updatedAt = goalCreated.plusDays(2), totalPrice = 999_000.0, balanceRemaining = 0.0),
                // Cycle 2 order — counted.
                order(updatedAt = nextCycleDay1, totalPrice = 25_000.0, balanceRemaining = 0.0)
            ),
            today = nextCycleDay1,
            goal = goal(),
            timeZone = tz
        )!!
        assertEquals(25_000.0, result.collectedAmount)
        // Day 1 of new cycle (one day in) → 5 days left after today.
        assertEquals(5, result.daysLeft)
    }

    @Test
    fun progressPercentDerivesFromCollectedOverTarget() {
        val result = WeeklyGoalCalculator.compute(
            orders = listOf(
                order(updatedAt = goalCreated, totalPrice = 25_000.0, balanceRemaining = 0.0)
            ),
            today = goalCreated,
            goal = goal(),
            timeZone = tz
        )!!
        assertEquals(0.25f, result.progressPercent)
    }

    private fun LocalDate.minusDays(n: Long): LocalDate =
        LocalDate.fromEpochDays((toEpochDays() - n).toInt())

    private fun LocalDate.plusDays(n: Long): LocalDate =
        LocalDate.fromEpochDays((toEpochDays() + n).toInt())
}
