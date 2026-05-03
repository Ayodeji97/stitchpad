package com.danzucker.stitchpad.feature.dashboard.domain

import com.danzucker.stitchpad.feature.dashboard.presentation.model.WeeklyGoalPace
import kotlin.test.Test
import kotlin.test.assertEquals

class WeeklyGoalPaceCalculatorTest {

    private val target = 100_000.0

    @Test
    fun zeroTargetReturnsOnPace() {
        // Defensive: avoid division-by-zero when the user somehow has a 0-target goal.
        val pace = WeeklyGoalPaceCalculator.compute(
            collectedAmount = 0.0,
            targetAmount = 0.0,
            daysLeft = 6
        )
        assertEquals(WeeklyGoalPace.OnPace, pace)
    }

    @Test
    fun progressAtOrAboveEightyFivePercentReturnsNearGoal() {
        // Even on day 1 (daysLeft=6) — near-goal beats "ahead" because the user
        // only needs a small push, not a celebration.
        val pace = WeeklyGoalPaceCalculator.compute(
            collectedAmount = 85_000.0,
            targetAmount = target,
            daysLeft = 6
        )
        assertEquals(WeeklyGoalPace.NearGoal, pace)
    }

    @Test
    fun progressWithinToleranceReturnsOnPace() {
        // daysElapsed = 1 → expected ≈ 14.3%. 10% collected → delta = -4.3%
        // which is inside the ±10% tolerance band → OnPace.
        val pace = WeeklyGoalPaceCalculator.compute(
            collectedAmount = 10_000.0,
            targetAmount = target,
            daysLeft = 6
        )
        assertEquals(WeeklyGoalPace.OnPace, pace)
    }

    @Test
    fun progressWellAboveExpectedReturnsAhead() {
        // daysElapsed = 1 → expected ≈ 14%. 50% collected → delta ≈ +36% → ahead.
        val pace = WeeklyGoalPaceCalculator.compute(
            collectedAmount = 50_000.0,
            targetAmount = target,
            daysLeft = 6
        )
        assertEquals(WeeklyGoalPace.Ahead, pace)
    }

    @Test
    fun progressWellBelowExpectedReturnsBehind() {
        // daysElapsed = 6 → expected ≈ 86%. 30% collected → delta ≈ -56% → behind.
        val pace = WeeklyGoalPaceCalculator.compute(
            collectedAmount = 30_000.0,
            targetAmount = target,
            daysLeft = 1
        )
        assertEquals(WeeklyGoalPace.Behind, pace)
    }

    @Test
    fun nearGoalBeatsBehindWhenAlmostThere() {
        // daysElapsed = 6 → expected ≈ 86%. 90% collected → would be on-pace,
        // but NearGoal triggers first because progress ≥ 85%.
        val pace = WeeklyGoalPaceCalculator.compute(
            collectedAmount = 90_000.0,
            targetAmount = target,
            daysLeft = 1
        )
        assertEquals(WeeklyGoalPace.NearGoal, pace)
    }

    @Test
    fun negativeCollectedDoesNotCrash() {
        // Pathological input (e.g. balance > total) — calculator should treat
        // it as 0% progress, which on day 1 (~14% expected) reads as Behind.
        val pace = WeeklyGoalPaceCalculator.compute(
            collectedAmount = -10_000.0,
            targetAmount = target,
            daysLeft = 6
        )
        assertEquals(WeeklyGoalPace.Behind, pace)
    }
}
