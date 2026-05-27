package com.danzucker.stitchpad.feature.measurement.data

import com.danzucker.stitchpad.core.data.dto.CustomMeasurementFieldDto
import com.danzucker.stitchpad.core.data.mapper.toCustomMeasurementField
import com.danzucker.stitchpad.core.data.mapper.toCustomMeasurementFieldDto
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.CustomMeasurementField
import com.danzucker.stitchpad.core.domain.repository.CustomMeasurementFieldRepository
import com.danzucker.stitchpad.core.logging.AppLogger
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

private const val TAG = "CustomFieldRepo"

class FirebaseCustomMeasurementFieldRepository(
    private val firestore: FirebaseFirestore,
) : CustomMeasurementFieldRepository {

    private fun collection(userId: String) =
        firestore.collection("users")
            .document(userId)
            .collection("customMeasurementFields")

    override fun observeFields(
        userId: String,
    ): Flow<Result<List<CustomMeasurementField>, DataError.Network>> =
        collection(userId)
            .snapshots()
            .map { snapshot ->
                val fields = snapshot.documents
                    .mapNotNull { doc ->
                        runCatching { doc.data<CustomMeasurementFieldDto>().toCustomMeasurementField() }.getOrNull()
                    }
                    .sortedBy { it.createdAt }
                Result.Success(fields) as Result<List<CustomMeasurementField>, DataError.Network>
            }
            .catch { throwable ->
                AppLogger.e(tag = TAG, throwable = throwable) { "observeFields failed" }
                emit(Result.Error(DataError.Network.UNKNOWN))
            }

    override suspend fun createField(
        userId: String,
        field: CustomMeasurementField,
    ): EmptyResult<DataError.Network> {
        val docRef = if (field.id.isBlank()) {
            collection(userId).document
        } else {
            collection(userId).document(field.id)
        }
        return try {
            val dto = field.toCustomMeasurementFieldDto().copy(id = docRef.id)
            docRef.set(dto)
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) {
                "createField failed fieldId=${docRef.id}"
            }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    override suspend fun updateField(
        userId: String,
        field: CustomMeasurementField,
    ): EmptyResult<DataError.Network> {
        return try {
            val now = Clock.System.now().toEpochMilliseconds()
            val dto = field.copy(updatedAt = now).toCustomMeasurementFieldDto()
            collection(userId).document(field.id).set(dto)
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) {
                "updateField failed fieldId=${field.id}"
            }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    override suspend fun archiveField(
        userId: String,
        fieldId: String,
    ): EmptyResult<DataError.Network> {
        return try {
            val now = Clock.System.now().toEpochMilliseconds()
            collection(userId).document(fieldId).set(
                mapOf("isArchived" to true, "updatedAt" to now),
                merge = true,
            )
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) {
                "archiveField failed fieldId=$fieldId"
            }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }
}
