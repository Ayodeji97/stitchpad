package com.danzucker.stitchpad.feature.goals.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class WeeklyGoalDto(
    val targetAmount: Double = 0.0,
    val updatedAt: Long = 0L
)
