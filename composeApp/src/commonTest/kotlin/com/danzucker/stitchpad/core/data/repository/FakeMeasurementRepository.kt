package com.danzucker.stitchpad.core.data.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.repository.MeasurementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeMeasurementRepository : MeasurementRepository {
    var observeError: DataError.Network? = null
    var operationError: DataError.Network? = null
    var lastCreatedMeasurement: Measurement? = null
    var lastUpdatedMeasurement: Measurement? = null
    var lastDeletedMeasurementId: String? = null

    // StateFlow-backed so tests can mutate the list AFTER a ViewModel started
    // observing and the observer re-emits — mirrors FakeCustomerRepository.
    private val measurementsFlow = MutableStateFlow<List<Measurement>>(emptyList())
    var measurementsList: List<Measurement>
        get() = measurementsFlow.value
        set(value) { measurementsFlow.value = value }

    override fun observeMeasurements(
        userId: String,
        customerId: String,
    ): Flow<Result<List<Measurement>, DataError.Network>> =
        measurementsFlow.map { list ->
            observeError?.let { return@map Result.Error(it) }
            Result.Success(list)
        }

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
