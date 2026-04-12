package com.danzucker.stitchpad.core.domain.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Measurement
import kotlinx.coroutines.flow.Flow

interface MeasurementRepository {
    fun observeMeasurements(
        userId: String,
        customerId: String
    ): Flow<Result<List<Measurement>, DataError.Network>>

    suspend fun createMeasurement(
        userId: String,
        customerId: String,
        measurement: Measurement
    ): EmptyResult<DataError.Network>

    suspend fun updateMeasurement(
        userId: String,
        customerId: String,
        measurement: Measurement
    ): EmptyResult<DataError.Network>

    suspend fun deleteMeasurement(
        userId: String,
        customerId: String,
        measurementId: String
    ): EmptyResult<DataError.Network>
}
