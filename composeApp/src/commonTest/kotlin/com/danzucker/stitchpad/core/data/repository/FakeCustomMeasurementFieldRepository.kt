package com.danzucker.stitchpad.core.data.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.CustomMeasurementField
import com.danzucker.stitchpad.core.domain.repository.CustomMeasurementFieldRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class FakeCustomMeasurementFieldRepository : CustomMeasurementFieldRepository {
    var observeError: DataError.Network? = null
    var operationError: DataError.Network? = null
    var lastCreatedField: CustomMeasurementField? = null
    var lastUpdatedField: CustomMeasurementField? = null
    var lastArchivedFieldId: String? = null

    private val _fields = MutableStateFlow<List<CustomMeasurementField>>(emptyList())

    /** Test seed helper — set the initial field list. */
    fun seedFields(fields: List<CustomMeasurementField>) {
        _fields.value = fields
    }

    override fun observeFields(
        userId: String,
    ): Flow<Result<List<CustomMeasurementField>, DataError.Network>> =
        _fields.asStateFlow().map { current ->
            observeError?.let { Result.Error(it) } ?: Result.Success(current)
        }

    override suspend fun createField(
        userId: String,
        field: CustomMeasurementField,
    ): EmptyResult<DataError.Network> {
        operationError?.let { return Result.Error(it) }
        lastCreatedField = field
        _fields.value = _fields.value + field
        return Result.Success(Unit)
    }

    override suspend fun updateField(
        userId: String,
        field: CustomMeasurementField,
    ): EmptyResult<DataError.Network> {
        operationError?.let { return Result.Error(it) }
        lastUpdatedField = field
        _fields.value = _fields.value.map { if (it.id == field.id) field else it }
        return Result.Success(Unit)
    }

    override suspend fun archiveField(
        userId: String,
        fieldId: String,
    ): EmptyResult<DataError.Network> {
        operationError?.let { return Result.Error(it) }
        lastArchivedFieldId = fieldId
        _fields.value = _fields.value.map {
            if (it.id == fieldId) it.copy(isArchived = true) else it
        }
        return Result.Success(Unit)
    }
}
