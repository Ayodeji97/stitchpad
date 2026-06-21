package com.danzucker.stitchpad.core.data.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.core.domain.model.StyleFolder
import com.danzucker.stitchpad.core.domain.model.StyleLocation
import com.danzucker.stitchpad.core.domain.repository.StyleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeStyleRepository : StyleRepository {
    var observeError: DataError.Network? = null
    var stylesList: List<Style> = emptyList()
    var operationError: DataError.Network? = null

    var lastCreatedDescription: String? = null
    var lastCreatedPhotoBytes: ByteArray? = null
    var lastBatchCreatedDescription: String? = null
    var lastBatchCreatedCount: Int? = null
    var lastUpdatedStyle: Style? = null
    var lastUpdatedPhotoBytes: ByteArray? = null
    var lastDeletedStyleId: String? = null
    var lastDeletedLocation: StyleLocation? = null
    var lastCopied: Triple<StyleLocation, String, StyleLocation>? = null  // (from, styleId, to)
    var lastMoved: Triple<StyleLocation, String, StyleLocation>? = null
    var lastSetTitleStyleId: String? = null
    var lastSetTitle: String? = null

    var folders: List<StyleFolder> = emptyList()
    var lastCreatedFolderName: String? = null
    var lastRenamedFolder: Pair<String, String>? = null  // (folderId, newName)
    var lastDeletedFolderId: String? = null

    /**
     * Per-location style overrides. When a [StyleLocation] key is present here,
     * [observeStyles] returns those styles instead of [stylesList]. This allows
     * tests to seed styles at specific locations (e.g. inside a named folder).
     */
    val stylesByLocation: MutableMap<StyleLocation, List<Style>> = mutableMapOf()

    override fun observeStyles(
        userId: String,
        location: StyleLocation,
    ): Flow<Result<List<Style>, DataError.Network>> =
        observeError?.let { flowOf(Result.Error(it)) }
            ?: flowOf(Result.Success(stylesByLocation[location] ?: stylesList))

    var lastCreatedStyleId: String = "fake-style-id"

    override suspend fun createStyle(
        userId: String,
        location: StyleLocation,
        description: String,
        photoBytes: ByteArray,
    ): Result<String, DataError.Network> {
        operationError?.let { return Result.Error(it) }
        lastCreatedDescription = description
        lastCreatedPhotoBytes = photoBytes
        return Result.Success(lastCreatedStyleId)
    }

    override suspend fun updateStyle(
        userId: String,
        location: StyleLocation,
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
        location: StyleLocation,
        style: Style,
    ): EmptyResult<DataError.Network> {
        operationError?.let { return Result.Error(it) }
        lastDeletedStyleId = style.id
        lastDeletedLocation = location
        return Result.Success(Unit)
    }

    override suspend fun createStyles(
        userId: String,
        location: StyleLocation,
        description: String,
        photoBytesList: List<ByteArray>,
    ): Result<List<String>, DataError.Network> {
        operationError?.let { return Result.Error(it) }
        if (photoBytesList.isEmpty()) return Result.Success(emptyList())
        lastBatchCreatedDescription = description
        lastBatchCreatedCount = photoBytesList.size
        val ids = photoBytesList.indices.map { index -> "fake-style-$index" }
        return Result.Success(ids)
    }

    override suspend fun copyStyle(
        userId: String,
        from: StyleLocation,
        style: Style,
        to: StyleLocation,
    ): EmptyResult<DataError.Network> {
        operationError?.let { return Result.Error(it) }
        lastCopied = Triple(from, style.id, to)
        return Result.Success(Unit)
    }

    override suspend fun moveStyle(
        userId: String,
        from: StyleLocation,
        style: Style,
        to: StyleLocation,
    ): EmptyResult<DataError.Network> {
        operationError?.let { return Result.Error(it) }
        lastMoved = Triple(from, style.id, to)
        return Result.Success(Unit)
    }

    override suspend fun setStyleTitle(
        userId: String,
        location: StyleLocation,
        styleId: String,
        title: String,
    ): EmptyResult<DataError.Network> {
        operationError?.let { return Result.Error(it) }
        lastSetTitleStyleId = styleId
        lastSetTitle = title.trim()
        val trimmed = title.trim()
        stylesList = stylesList.map { s -> if (s.id == styleId) s.copy(description = trimmed) else s }
        stylesByLocation.replaceAll { _, styles ->
            styles.map { s -> if (s.id == styleId) s.copy(description = trimmed) else s }
        }
        return Result.Success(Unit)
    }

    /**
     * Per-location folder overrides. When a [StyleLocation] key is present here,
     * [observeFolders] returns those folders instead of [folders].
     */
    val foldersByLocation: MutableMap<StyleLocation, List<StyleFolder>> = mutableMapOf()

    override fun observeFolders(
        userId: String,
        location: StyleLocation,
    ): Flow<Result<List<StyleFolder>, DataError.Network>> =
        observeError?.let { flowOf(Result.Error(it)) }
            ?: flowOf(Result.Success(foldersByLocation[location] ?: folders))

    override suspend fun createFolder(
        userId: String,
        location: StyleLocation,
        name: String,
    ): Result<String, DataError.Network> {
        operationError?.let { return Result.Error(it) }
        lastCreatedFolderName = name
        return Result.Success("fake-folder-id")
    }

    override suspend fun renameFolder(
        userId: String,
        location: StyleLocation,
        folderId: String,
        name: String,
    ): EmptyResult<DataError.Network> {
        operationError?.let { return Result.Error(it) }
        lastRenamedFolder = folderId to name
        return Result.Success(Unit)
    }

    override suspend fun deleteFolder(
        userId: String,
        location: StyleLocation,
        folderId: String,
    ): EmptyResult<DataError.Network> {
        operationError?.let { return Result.Error(it) }
        lastDeletedFolderId = folderId
        return Result.Success(Unit)
    }
}
