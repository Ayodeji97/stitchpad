package com.danzucker.stitchpad.feature.goals.data

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.goals.domain.model.WeeklyGoal
import com.danzucker.stitchpad.feature.goals.domain.repository.WeeklyGoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeWeeklyGoalRepository : WeeklyGoalRepository {
    var shouldReturnError: DataError.Network? = null
    var lastSavedGoal: WeeklyGoal? = null

    private val goalFlow = MutableStateFlow<WeeklyGoal?>(null)
    var storedGoal: WeeklyGoal?
        get() = goalFlow.value
        set(value) { goalFlow.value = value }

    override fun observeWeeklyGoal(
        userId: String
    ): Flow<Result<WeeklyGoal?, DataError.Network>> =
        goalFlow.map { current ->
            shouldReturnError?.let { return@map Result.Error(it) }
            Result.Success(current)
        }

    override suspend fun setWeeklyGoal(
        userId: String,
        goal: WeeklyGoal
    ): EmptyResult<DataError.Network> {
        shouldReturnError?.let { return Result.Error(it) }
        lastSavedGoal = goal
        goalFlow.value = goal
        return Result.Success(Unit)
    }
}
