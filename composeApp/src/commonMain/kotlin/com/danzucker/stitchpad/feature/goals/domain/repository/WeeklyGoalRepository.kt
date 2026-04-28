package com.danzucker.stitchpad.feature.goals.domain.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.goals.domain.model.WeeklyGoal
import kotlinx.coroutines.flow.Flow

interface WeeklyGoalRepository {
    /** Emits the user's current weekly goal, or `null` when none has been set. */
    fun observeWeeklyGoal(userId: String): Flow<Result<WeeklyGoal?, DataError.Network>>

    suspend fun setWeeklyGoal(userId: String, goal: WeeklyGoal): EmptyResult<DataError.Network>
}
