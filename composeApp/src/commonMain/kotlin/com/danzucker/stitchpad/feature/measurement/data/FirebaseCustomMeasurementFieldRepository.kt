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
import com.danzucker.stitchpad.core.offline.OfflineWriteDispatcher
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

private const val TAG = "CustomFieldRepo"

class FirebaseCustomMeasurementFieldRepository(
    private val firestore: FirebaseFirestore,
    private val offlineWrites: OfflineWriteDispatcher,
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
        val dto = field.toCustomMeasurementFieldDto().copy(id = docRef.id)
        val accepted = offlineWrites.enqueue("createCustomMeasurementField fieldId=${docRef.id}") {
            docRef.set(dto)
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        return Result.Success(Unit)
    }

    override suspend fun updateField(
        userId: String,
        field: CustomMeasurementField,
    ): EmptyResult<DataError.Network> {
        val now = Clock.System.now().toEpochMilliseconds()
        val dto = field.copy(updatedAt = now).toCustomMeasurementFieldDto()
        val accepted = offlineWrites.enqueue("updateCustomMeasurementField fieldId=${field.id}") {
            collection(userId).document(field.id).set(dto)
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        return Result.Success(Unit)
    }

    override suspend fun archiveField(
        userId: String,
        fieldId: String,
    ): EmptyResult<DataError.Network> {
        val now = Clock.System.now().toEpochMilliseconds()
        val accepted = offlineWrites.enqueue("archiveCustomMeasurementField fieldId=$fieldId") {
            collection(userId).document(fieldId).set(
                mapOf("isArchived" to true, "updatedAt" to now),
                merge = true,
            )
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        return Result.Success(Unit)
    }
}
