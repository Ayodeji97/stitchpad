package com.danzucker.stitchpad.core.data.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.CustomGarmentType
import com.danzucker.stitchpad.core.domain.repository.CustomGarmentTypeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeCustomGarmentTypeRepository : CustomGarmentTypeRepository {

    var shouldReturnError: DataError.Network? = null

    private val customsFlow = MutableStateFlow<Map<String, List<CustomGarmentType>>>(emptyMap())

    var upsertCalls: MutableList<Pair<String, String>> = mutableListOf()
        private set

    var touchCalls: MutableList<Pair<String, String>> = mutableListOf()
        private set

    fun seed(userId: String, customs: List<CustomGarmentType>) {
        customsFlow.value = customsFlow.value + (userId to customs)
    }

    override fun observe(
        userId: String
    ): Flow<Result<List<CustomGarmentType>, DataError.Network>> =
        customsFlow.map { byUser ->
            shouldReturnError?.let { Result.Error(it) }
                ?: Result.Success(
                    byUser[userId].orEmpty().sortedWith(
                        compareByDescending<CustomGarmentType> { it.lastUsedAt }
                            .thenBy { it.name.lowercase() }
                    )
                ) as Result<List<CustomGarmentType>, DataError.Network>
        }

    override suspend fun upsert(
        userId: String,
        name: String
    ): Result<CustomGarmentType, DataError.Network> {
        upsertCalls += userId to name
        shouldReturnError?.let { return Result.Error(it) }
        val trimmed = name.trim()
        val now = 1_000L * (upsertCalls.size)
        val existing = customsFlow.value[userId].orEmpty()
            .firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
        return if (existing != null) {
            val updated = existing.copy(lastUsedAt = now)
            customsFlow.value = customsFlow.value + (userId to
                (customsFlow.value[userId].orEmpty().map { if (it.id == existing.id) updated else it })
            )
            Result.Success(updated)
        } else {
            val created = CustomGarmentType(
                id = "fake-id-${upsertCalls.size}",
                name = trimmed,
                createdAt = now,
                lastUsedAt = now,
            )
            customsFlow.value = customsFlow.value + (userId to
                (customsFlow.value[userId].orEmpty() + created)
            )
            Result.Success(created)
        }
    }

    override suspend fun touch(
        userId: String,
        id: String
    ): EmptyResult<DataError.Network> {
        touchCalls += userId to id
        return shouldReturnError?.let { Result.Error(it) } ?: Result.Success(Unit)
    }
}
