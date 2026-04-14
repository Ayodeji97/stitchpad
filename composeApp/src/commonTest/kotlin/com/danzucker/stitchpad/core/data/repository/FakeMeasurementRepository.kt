package com.danzucker.stitchpad.core.data.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.repository.MeasurementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeMeasurementRepository : MeasurementRepository {
    var observeError: DataError.Network? = null
    var measurementsList: List<Measurement> = emptyList()
    var operationError: DataError.Network? = null
    var lastCreatedMeasurement: Measurement? = null
    var lastUpdatedMeasurement: Measurement? = null
    var lastDeletedMeasurementId: String? = null

    override fun observeMeasurements(
        userId: String,
        customerId: String,
    ): Flow<Result<List<Measurement>, DataError.Network>> =
        observeError?.let { flowOf(Result.Error(it)) }
            ?: flowOf(Result.Success(measurementsList))

    override suspend fun createMeasurement(
        userId: String,
        customerId: String,
        measurement: Measurement,
    ): EmptyResult<DataError.Network> {
        operationError?.let { return Result.Error(it) }
        lastCreatedMeasurement = measurement
        return Result.Success(Unit)
    }

    override suspend fun updateMeasurement(
        userId: String,
        customerId: String,
        measurement: Measurement,
    ): EmptyResult<DataError.Network> {
        operationError?.let { return Result.Error(it) }
        lastUpdatedMeasurement = measurement
        return Result.Success(Unit)
    }

    override suspend fun deleteMeasurement(
        userId: String,
        customerId: String,
        measurementId: String,
    ): EmptyResult<DataError.Network> {
        operationError?.let { return Result.Error(it) }
        lastDeletedMeasurementId = measurementId
        return Result.Success(Unit)
    }
}
