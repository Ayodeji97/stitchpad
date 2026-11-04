package com.danzucker.stitchpad.feature.goals.data.mapper

import com.danzucker.stitchpad.core.domain.model.Currency
import com.danzucker.stitchpad.feature.goals.data.dto.WeeklyGoalDto
import com.danzucker.stitchpad.feature.goals.domain.model.WeeklyGoal
import kotlin.test.Test
import kotlin.test.assertEquals

class WeeklyGoalCurrencyMapperTest {
    @Test
    fun legacyAndUnknownGoalCurrencyFallsBackToNigeriaWhileNewGoalsRoundTrip() {
        assertEquals(Currency.NGN, WeeklyGoalDto(targetAmount = 1000.0).toWeeklyGoal().currency)
        assertEquals(Currency.NGN, WeeklyGoalDto(currency = "unknown").toWeeklyGoal().currency)
        val goal = WeeklyGoal(500.0, 100L, Currency.GHS)
        assertEquals(goal, goal.toWeeklyGoalDto().toWeeklyGoal())
    }
}
