package com.danzucker.stitchpad.feature.goals.data.mapper

import com.danzucker.stitchpad.feature.goals.data.dto.WeeklyGoalDto
import com.danzucker.stitchpad.feature.goals.domain.model.WeeklyGoal

fun WeeklyGoalDto.toWeeklyGoal(): WeeklyGoal = WeeklyGoal(
    targetAmount = targetAmount,
    updatedAt = updatedAt
)

fun WeeklyGoal.toWeeklyGoalDto(): WeeklyGoalDto = WeeklyGoalDto(
    targetAmount = targetAmount,
    updatedAt = updatedAt
)
