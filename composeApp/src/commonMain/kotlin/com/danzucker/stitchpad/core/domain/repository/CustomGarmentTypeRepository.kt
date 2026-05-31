package com.danzucker.stitchpad.core.domain.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.CustomGarmentType
import kotlinx.coroutines.flow.Flow

interface CustomGarmentTypeRepository {

    /**
     * Subscribe to the tailor's saved customs.
     * Emits a list sorted by lastUsedAt desc, with an alphabetical tiebreak.
     */
    fun observe(userId: String): Flow<Result<List<CustomGarmentType>, DataError.Network>>

    /**
     * Create a new custom OR return the existing one if a case-insensitive
     * name match already exists. Always updates lastUsedAt = now on the
     * resolved doc so the resulting entry sorts to the top of the picker.
     */
    suspend fun upsert(
        userId: String,
        name: String
    ): Result<CustomGarmentType, DataError.Network>

    /**
     * Bump lastUsedAt on an existing custom. Called fire-and-forget from
     * the form when the user picks an already-saved custom value.
     */
    suspend fun touch(userId: String, id: String): EmptyResult<DataError.Network>
}
