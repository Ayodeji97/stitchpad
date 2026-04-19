package com.danzucker.stitchpad.feature.measurement.data

import com.danzucker.stitchpad.core.data.dto.MeasurementDto
import com.danzucker.stitchpad.core.data.mapper.toMeasurement
import com.danzucker.stitchpad.core.data.mapper.toMeasurementDto
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.repository.MeasurementRepository
import com.danzucker.stitchpad.core.logging.AppLogger
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private const val TAG = "MeasurementRepo"

class FirebaseMeasurementRepository(
    private val firestore: FirebaseFirestore
) : MeasurementRepository {

    private fun measurementsCollection(userId: String, customerId: String) =
        firestore.collection("users")
            .document(userId)
            .collection("customers")
            .document(customerId)
            .collection("measurements")

    override fun observeMeasurements(
        userId: String,
        customerId: String
    ): Flow<Result<List<Measurement>, DataError.Network>> =
        measurementsCollection(userId, customerId)
            .snapshots()
            .map { snapshot ->
                val measurements = snapshot.documents
                    .mapNotNull { doc ->
                        runCatching { doc.data<MeasurementDto>().toMeasurement(customerId) }.getOrNull()
                    }
                    .sortedByDescending { it.createdAt }
                Result.Success(measurements) as Result<List<Measurement>, DataError.Network>
            }
            .catch { throwable ->
                AppLogger.e(tag = TAG, throwable = throwable) { "observeMeasurements failed" }
                emit(Result.Error(DataError.Network.UNKNOWN))
            }

    override suspend fun createMeasurement(
        userId: String,
        customerId: String,
        measurement: Measurement
    ): EmptyResult<DataError.Network> {
        return try {
            val docRef = if (measurement.id.isBlank()) {
                measurementsCollection(userId, customerId).document
            } else {
                measurementsCollection(userId, customerId).document(measurement.id)
            }
            val dto = measurement.toMeasurementDto().copy(id = docRef.id)
            docRef.set(dto)
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) {
                "createMeasurement failed measurementId=${measurement.id}"
            }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    override suspend fun updateMeasurement(
        userId: String,
        customerId: String,
        measurement: Measurement
    ): EmptyResult<DataError.Network> {
        return try {
            measurementsCollection(userId, customerId)
                .document(measurement.id)
                .set(measurement.toMeasurementDto())
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) {
                "updateMeasurement failed measurementId=${measurement.id}"
            }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    override suspend fun deleteMeasurement(
        userId: String,
        customerId: String,
        measurementId: String
    ): EmptyResult<DataError.Network> {
        return try {
            measurementsCollection(userId, customerId)
                .document(measurementId)
                .delete()
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) {
                "deleteMeasurement failed measurementId=$measurementId"
            }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }
}
