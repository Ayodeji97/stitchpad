package com.danzucker.stitchpad.core.data.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.core.domain.repository.StyleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeStyleRepository : StyleRepository {
    var observeError: DataError.Network? = null
    var stylesList: List<Style> = emptyList()
    var operationError: DataError.Network? = null

    var lastCreatedDescription: String? = null
    var lastCreatedPhotoBytes: ByteArray? = null
    var lastUpdatedStyle: Style? = null
    var lastUpdatedPhotoBytes: ByteArray? = null
    var lastDeletedStyleId: String? = null

    override fun observeStyles(
        userId: String,
        customerId: String,
    ): Flow<Result<List<Style>, DataError.Network>> =
        observeError?.let { flowOf(Result.Error(it)) }
            ?: flowOf(Result.Success(stylesList))

    override suspend fun createStyle(
        userId: String,
        customerId: String,
        description: String,
        photoBytes: ByteArray,
    ): EmptyResult<DataError.Network> {
        operationError?.let { return Result.Error(it) }
        lastCreatedDescription = description
        lastCreatedPhotoBytes = photoBytes
        return Result.Success(Unit)
    }

    override suspend fun updateStyle(
        userId: String,
        customerId: String,
        style: Style,
        newPhotoBytes: ByteArray?,
    ): EmptyResult<DataError.Network> {
        operationError?.let { return Result.Error(it) }
        lastUpdatedStyle = style
        lastUpdatedPhotoBytes = newPhotoBytes
        return Result.Success(Unit)
    }

    override suspend fun deleteStyle(
        userId: String,
        customerId: String,
        style: Style,
    ): EmptyResult<DataError.Network> {
        operationError?.let { return Result.Error(it) }
        lastDeletedStyleId = style.id
        return Result.Success(Unit)
    }
}
