package com.danzucker.stitchpad.core.domain.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Style
import kotlinx.coroutines.flow.Flow

interface StyleRepository {
    fun observeStyles(
        userId: String,
        customerId: String
    ): Flow<Result<List<Style>, DataError.Network>>

    suspend fun createStyle(
        userId: String,
        customerId: String,
        description: String,
        photoBytes: ByteArray
    ): EmptyResult<DataError.Network>

    suspend fun updateStyle(
        userId: String,
        customerId: String,
        style: Style,
        newPhotoBytes: ByteArray?
    ): EmptyResult<DataError.Network>

    suspend fun deleteStyle(
        userId: String,
        customerId: String,
        style: Style
    ): EmptyResult<DataError.Network>
}
