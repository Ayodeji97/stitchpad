package com.danzucker.stitchpad.core.domain.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.core.domain.model.StyleFolder
import com.danzucker.stitchpad.core.domain.model.StyleLocation
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
interface StyleRepository {
    fun observeStyles(
        userId: String,
        location: StyleLocation
    ): Flow<Result<List<Style>, DataError.Network>>

    fun observeFolders(
        userId: String,
        location: StyleLocation,
    ): Flow<Result<List<StyleFolder>, DataError.Network>>

    suspend fun createFolder(
        userId: String,
        location: StyleLocation,
        name: String,
    ): Result<String, DataError.Network>

    suspend fun renameFolder(
        userId: String,
        location: StyleLocation,
        folderId: String,
        name: String,
    ): EmptyResult<DataError.Network>

    suspend fun deleteFolder(
        userId: String,
        location: StyleLocation,
        folderId: String,
    ): EmptyResult<DataError.Network>

    suspend fun createStyle(
        userId: String,
        location: StyleLocation,
        description: String,
        photoBytes: ByteArray
    ): Result<String, DataError.Network>

    suspend fun createStyles(
        userId: String,
        location: StyleLocation,
        description: String,
        photoBytesList: List<ByteArray>,
    ): Result<List<String>, DataError.Network>

    suspend fun updateStyle(
        userId: String,
        location: StyleLocation,
        style: Style,
        newPhotoBytes: ByteArray?
    ): EmptyResult<DataError.Network>

    suspend fun deleteStyle(
        userId: String,
        location: StyleLocation,
        style: Style
    ): EmptyResult<DataError.Network>

    /**
     * Copy [style] from [from] location into [to] location. The new style shares the source
     * image (no re-upload); the source stays in place. Style deletes never remove
     * the storage object (see [deleteStyle]), so neither copy ever loses its photo.
     */
    suspend fun copyStyle(
        userId: String,
        from: StyleLocation,
        style: Style,
        to: StyleLocation,
    ): EmptyResult<DataError.Network>

    /**
     * Move [style] from [from] location to [to] location: create the target
     * (sharing the image), then remove the source Firestore doc only (the image
     * stays, now owned by the target).
     */
    suspend fun moveStyle(
        userId: String,
        from: StyleLocation,
        style: Style,
        to: StyleLocation,
    ): EmptyResult<DataError.Network>
}
