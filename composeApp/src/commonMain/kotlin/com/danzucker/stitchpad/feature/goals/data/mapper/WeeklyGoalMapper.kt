package com.danzucker.stitchpad.feature.goals.data.mapper

import com.danzucker.stitchpad.core.domain.model.Currency
import com.danzucker.stitchpad.feature.goals.data.dto.WeeklyGoalDto
import com.danzucker.stitchpad.feature.goals.domain.model.WeeklyGoal

fun WeeklyGoalDto.toWeeklyGoal(): WeeklyGoal = WeeklyGoal(
    targetAmount = targetAmount,
    updatedAt = updatedAt,
    currency = Currency.fromCode(currency) ?: Currency.NGN,
)

fun WeeklyGoal.toWeeklyGoalDto(): WeeklyGoalDto = WeeklyGoalDto(
    targetAmount = targetAmount,
    updatedAt = updatedAt,
    currency = currency.code,
)
