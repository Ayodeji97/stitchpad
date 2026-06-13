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
    ): Result<String, DataError.Network>

    suspend fun createStyles(
        userId: String,
        customerId: String,
        description: String,
        photoBytesList: List<ByteArray>,
    ): Result<List<String>, DataError.Network>

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

    /**
     * Copy [style] into [toCustomerId]'s closet. The new style shares the source
     * image (no re-upload) — both ends are flagged sharesImage so deleting either
     * never orphans the other's photo. The source stays in place.
     */
    suspend fun copyStyle(
        userId: String,
        fromCustomerId: String,
        style: Style,
        toCustomerId: String,
    ): EmptyResult<DataError.Network>

    /**
     * Move [style] from [fromCustomerId] to [toCustomerId]: create the target
     * (sharing the image), then remove the source Firestore doc only (the image
     * stays, now owned by the target).
     */
    suspend fun moveStyle(
        userId: String,
        fromCustomerId: String,
        style: Style,
        toCustomerId: String,
    ): EmptyResult<DataError.Network>
}
