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

    /**
     * Optional per-customer override, backward-compatible with [measurementsList].
     * When a test seeds `measurementsForCustomer[customerId]`, [observeMeasurements]
     * returns that list for the matching customer; otherwise it falls back to the
     * single flat [measurementsList] (existing tests that never touch this map
     * keep observing the same thing they always did).
     */
    val measurementsForCustomer: MutableMap<String, List<Measurement>> = mutableMapOf()

    /**
     * Optional per-customer error override, checked before [observeError] and
     * [measurementsForCustomer]. Lets a test fail a single customer's fetch
     * (e.g. the Dashboard measurements picker) without affecting every other
     * customer's flow.
     */
    val errorForCustomer: MutableMap<String, DataError.Network> = mutableMapOf()

    override fun observeMeasurements(
        userId: String,
        customerId: String,
    ): Flow<Result<List<Measurement>, DataError.Network>> =
        measurementsFlow.map { list ->
            errorForCustomer[customerId]?.let { return@map Result.Error(it) }
            observeError?.let { return@map Result.Error(it) }
            val effectiveList = measurementsForCustomer[customerId] ?: list
            Result.Success(effectiveList)
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
