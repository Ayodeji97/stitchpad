package com.danzucker.stitchpad.feature.goals.data

import com.danzucker.stitchpad.core.data.retryWithFallback
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.offline.OfflineWriteDispatcher
import com.danzucker.stitchpad.feature.goals.data.dto.WeeklyGoalDto
import com.danzucker.stitchpad.feature.goals.data.mapper.toWeeklyGoal
import com.danzucker.stitchpad.feature.goals.data.mapper.toWeeklyGoalDto
import com.danzucker.stitchpad.feature.goals.domain.model.WeeklyGoal
import com.danzucker.stitchpad.feature.goals.domain.repository.WeeklyGoalRepository
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG = "WeeklyGoalRepo"
private const val GOAL_DOC_ID = "weekly"

class FirebaseWeeklyGoalRepository(
    private val firestore: FirebaseFirestore,
    private val offlineWrites: OfflineWriteDispatcher,
) : WeeklyGoalRepository {

    override fun observeWeeklyGoal(
        userId: String
    ): Flow<Result<WeeklyGoal?, DataError.Network>> =
        firestore.collection("users")
            .document(userId)
            .collection("goals")
            .document(GOAL_DOC_ID)
            .snapshots
            .map { snapshot ->
                val goal = if (snapshot.exists) {
                    runCatching { snapshot.data<WeeklyGoalDto>().toWeeklyGoal() }.getOrNull()
                } else {
                    null
                }
                Result.Success(goal) as Result<WeeklyGoal?, DataError.Network>
            }
            .retryWithFallback(fallback = Result.Error(DataError.Network.UNKNOWN)) { throwable, attempt ->
                AppLogger.w(tag = TAG, throwable = throwable) {
                    "observeWeeklyGoal failed; retrying (attempt ${attempt + 1})"
                }
            }

    override suspend fun setWeeklyGoal(
        userId: String,
        goal: WeeklyGoal
    ): EmptyResult<DataError.Network> {
        val accepted = offlineWrites.enqueue("setWeeklyGoal") {
            firestore.collection("users")
                .document(userId)
                .collection("goals")
                .document(GOAL_DOC_ID)
                .set(goal.toWeeklyGoalDto())
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        return Result.Success(Unit)
    }
}
