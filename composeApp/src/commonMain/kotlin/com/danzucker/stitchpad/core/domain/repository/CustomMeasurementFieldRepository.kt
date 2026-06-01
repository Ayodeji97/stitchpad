package com.danzucker.stitchpad.core.domain.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.CustomMeasurementField
import kotlinx.coroutines.flow.Flow

interface CustomMeasurementFieldRepository {
    fun observeFields(
        userId: String,
    ): Flow<Result<List<CustomMeasurementField>, DataError.Network>>

    suspend fun createField(
        userId: String,
        field: CustomMeasurementField,
    ): EmptyResult<DataError.Network>

    suspend fun updateField(
        userId: String,
        field: CustomMeasurementField,
    ): EmptyResult<DataError.Network>

    suspend fun archiveField(
        userId: String,
        fieldId: String,
    ): EmptyResult<DataError.Network>
}
