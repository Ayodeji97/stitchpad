package com.danzucker.stitchpad.feature.style.data

import com.danzucker.stitchpad.core.data.dto.StyleDto
import com.danzucker.stitchpad.core.data.dto.StyleFolderDto
import com.danzucker.stitchpad.core.data.mapper.toDto
import com.danzucker.stitchpad.core.data.mapper.toStyle
import com.danzucker.stitchpad.core.data.mapper.toStyleDto
import com.danzucker.stitchpad.core.data.mapper.toStyleFolder
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.ImageSyncState
import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.core.domain.model.StyleFolder
import com.danzucker.stitchpad.core.domain.model.StyleLocation
import com.danzucker.stitchpad.core.domain.repository.StyleRepository
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.offline.OfflinePhotoStore
import com.danzucker.stitchpad.core.offline.OfflineUploadJob
import com.danzucker.stitchpad.core.offline.OfflineUploadJobType
import com.danzucker.stitchpad.core.offline.OfflineUploadOutbox
import com.danzucker.stitchpad.core.offline.OfflineWriteDispatcher
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

private const val TAG = "StyleRepo"

@Suppress("TooManyFunctions")
class FirebaseStyleRepository(
    private val firestore: FirebaseFirestore,
    private val offlineWrites: OfflineWriteDispatcher,
    private val photoStore: OfflinePhotoStore,
    private val uploadOutbox: OfflineUploadOutbox,
) : StyleRepository {

    private fun collectionFor(userId: String, location: StyleLocation) = when (location) {
        is StyleLocation.CustomerCloset -> {
            val base = firestore.collection("users").document(userId)
                .collection("customers").document(location.customerId)
            val fid = location.folderId
            if (fid != null) {
                base.collection("styleFolders").document(fid).collection("styles")
            } else {
                base.collection("styles")
            }
        }
        is StyleLocation.Inspiration -> {
            val fid = location.folderId
            if (fid != null) {
                firestore.collection("users").document(userId)
                    .collection("inspirationFolders").document(fid).collection("styles")
            } else {
                firestore.collection("users").document(userId).collection("inspiration")
            }
        }
    }

    private fun storagePathFor(userId: String, location: StyleLocation, styleId: String): String =
        when (location) {
            is StyleLocation.CustomerCloset -> {
                val fid = location.folderId
                if (fid != null) {
                    "users/$userId/customers/${location.customerId}/styleFolders/$fid/$styleId.jpg"
                } else {
                    "users/$userId/customers/${location.customerId}/styles/$styleId.jpg"
                }
            }
            is StyleLocation.Inspiration -> {
                val fid = location.folderId
                if (fid != null) {
                    "users/$userId/inspirationFolders/$fid/$styleId.jpg"
                } else {
                    "users/$userId/inspiration/$styleId.jpg"
                }
            }
        }

    private fun foldersCollectionFor(userId: String, location: StyleLocation) = when (location) {
        is StyleLocation.CustomerCloset ->
            firestore.collection("users").document(userId)
                .collection("customers").document(location.customerId)
                .collection("styleFolders")
        is StyleLocation.Inspiration ->
            firestore.collection("users").document(userId).collection("inspirationFolders")
    }

    private fun uploadJobId(path: String): String =
        "${OfflineUploadJobType.STYLE_GALLERY_IMAGE}:$path"

    private fun Style.withLocalPendingPhoto(): Style =
        if (syncState == ImageSyncState.PENDING) {
            copy(localPhotoPath = uploadOutbox.localPathForStoragePath(photoStoragePath))
        } else {
            this
        }

    override fun observeFolders(
        userId: String,
        location: StyleLocation,
    ): Flow<Result<List<StyleFolder>, DataError.Network>> =
        foldersCollectionFor(userId, location)
            .snapshots()
            .map<_, Result<List<StyleFolder>, DataError.Network>> { snapshot ->
                val folders = snapshot.documents
                    .mapNotNull { doc ->
                        runCatching { doc.data<StyleFolderDto>().toStyleFolder() }.getOrNull()
                    }
                    .sortedByDescending { it.createdAt }
                Result.Success(folders)
            }
            .catch { throwable ->
                AppLogger.e(tag = TAG, throwable = throwable) { "observeFolders failed" }
                emit(Result.Error(DataError.Network.UNKNOWN))
            }

    override suspend fun createFolder(
        userId: String,
        location: StyleLocation,
        name: String,
    ): Result<String, DataError.Network> {
        val ref = foldersCollectionFor(userId, location).document
        return try {
            val accepted = offlineWrites.enqueue("createFolder folderId=${ref.id}") {
                ref.set(StyleFolder(id = ref.id, name = name, createdAt = 0L, updatedAt = 0L).toDto())
            }
            if (accepted) Result.Success(ref.id) else Result.Error(DataError.Network.UNKNOWN)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "createFolder failed folderId=${ref.id}" }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    override suspend fun renameFolder(
        userId: String,
        location: StyleLocation,
        folderId: String,
        name: String,
    ): EmptyResult<DataError.Network> =
        try {
            val now = Clock.System.now().toEpochMilliseconds()
            val accepted = offlineWrites.enqueue("renameFolder folderId=$folderId") {
                foldersCollectionFor(userId, location).document(folderId)
                    .update("name" to name, "updatedAt" to now)
            }
            if (accepted) Result.Success(Unit) else Result.Error(DataError.Network.UNKNOWN)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "renameFolder failed folderId=$folderId" }
            Result.Error(DataError.Network.UNKNOWN)
        }

    @Suppress("ReturnCount")
    override suspend fun deleteFolder(
        userId: String,
        location: StyleLocation,
        folderId: String,
    ): EmptyResult<DataError.Network> =
        try {
            val folderRef = foldersCollectionFor(userId, location).document(folderId)
            val stylesSnapshot = folderRef.collection("styles").get()
            val accepted = offlineWrites.enqueue("deleteFolder folderId=$folderId") {
                stylesSnapshot.documents.forEach { doc -> doc.reference.delete() }
                folderRef.delete()
            }
            if (accepted) Result.Success(Unit) else Result.Error(DataError.Network.UNKNOWN)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "deleteFolder failed folderId=$folderId" }
            Result.Error(DataError.Network.UNKNOWN)
        }

    override fun observeStyles(
        userId: String,
        location: StyleLocation
    ): Flow<Result<List<Style>, DataError.Network>> =
        collectionFor(userId, location)
            .snapshots()
            .map<_, Result<List<Style>, DataError.Network>> { snapshot ->
                val styles = snapshot.documents
                    .mapNotNull { doc ->
                        runCatching {
                            doc.data<StyleDto>().toStyle(location).withLocalPendingPhoto()
                        }.getOrNull()
                    }
                    .sortedByDescending { it.createdAt }
                Result.Success(styles)
            }
            .catch { throwable ->
                AppLogger.e(tag = TAG, throwable = throwable) { "observeStyles failed" }
                emit(Result.Error(DataError.Network.UNKNOWN))
            }

    override suspend fun createStyle(
        userId: String,
        location: StyleLocation,
        description: String,
        photoBytes: ByteArray
    ): Result<String, DataError.Network> {
        val docRef = collectionFor(userId, location).document
        val path = storagePathFor(userId, location, docRef.id)
        return try {
            val localPath = photoStore.save(photoBytes, "style-${docRef.id}.jpg")
            val style = Style(
                id = docRef.id,
                customerId = (location as? StyleLocation.CustomerCloset)?.customerId.orEmpty(),
                description = description,
                photoUrl = "",
                photoStoragePath = path,
                syncState = ImageSyncState.PENDING,
                localPhotoPath = localPath,
                createdAt = 0L,
                updatedAt = 0L
            )
            val accepted = offlineWrites.enqueue("createStyle styleId=${docRef.id}") {
                docRef.set(style.toStyleDto())
            }
            if (!accepted) {
                return Result.Error(DataError.Network.UNKNOWN)
            }
            uploadOutbox.enqueue(
                OfflineUploadJob(
                    id = uploadJobId(path),
                    type = OfflineUploadJobType.STYLE_GALLERY_IMAGE,
                    userId = userId,
                    customerId = (location as? StyleLocation.CustomerCloset)?.customerId.orEmpty(),
                    styleId = docRef.id,
                    storagePath = path,
                    localPath = localPath,
                    inspirationStyle = location is StyleLocation.Inspiration,
                    folderId = (location as? StyleLocation.CustomerCloset)?.folderId
                        ?: (location as? StyleLocation.Inspiration)?.folderId,
                )
            )
            Result.Success(docRef.id)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) {
                "createStyle failed styleId=${docRef.id}"
            }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    @Suppress("ReturnCount")
    override suspend fun createStyles(
        userId: String,
        location: StyleLocation,
        description: String,
        photoBytesList: List<ByteArray>,
    ): Result<List<String>, DataError.Network> {
        if (photoBytesList.isEmpty()) return Result.Success(emptyList())
        val createdIds = mutableListOf<String>()
        photoBytesList.forEach { bytes ->
            when (val result = createStyle(userId, location, description, bytes)) {
                is Result.Success -> createdIds += result.data
                is Result.Error -> return Result.Error(result.error)
            }
        }
        return Result.Success(createdIds)
    }

    override suspend fun updateStyle(
        userId: String,
        location: StyleLocation,
        style: Style,
        newPhotoBytes: ByteArray?
    ): EmptyResult<DataError.Network> {
        return try {
            val accepted = if (newPhotoBytes != null) {
                val path = style.photoStoragePath.ifBlank {
                    storagePathFor(userId, location, style.id)
                }
                val localPath = photoStore.save(newPhotoBytes, "style-${style.id}.jpg")
                val pendingStyle = style.copy(
                    photoStoragePath = path,
                    syncState = ImageSyncState.PENDING,
                    localPhotoPath = localPath,
                )
                val accepted = offlineWrites.enqueue("updateStylePhoto styleId=${style.id}") {
                    collectionFor(userId, location)
                        .document(pendingStyle.id)
                        .set(pendingStyle.toStyleDto())
                }
                if (accepted) {
                    uploadOutbox.enqueue(
                        OfflineUploadJob(
                            id = uploadJobId(path),
                            type = OfflineUploadJobType.STYLE_GALLERY_IMAGE,
                            userId = userId,
                            customerId = (location as? StyleLocation.CustomerCloset)?.customerId.orEmpty(),
                            styleId = style.id,
                            storagePath = path,
                            localPath = localPath,
                            inspirationStyle = location is StyleLocation.Inspiration,
                            folderId = (location as? StyleLocation.CustomerCloset)?.folderId
                                ?: (location as? StyleLocation.Inspiration)?.folderId,
                        )
                    )
                }
                accepted
            } else {
                offlineWrites.enqueue("updateStyle styleId=${style.id}") {
                    collectionFor(userId, location)
                        .document(style.id)
                        .set(style.toStyleDto())
                }
            }
            if (accepted) {
                Result.Success(Unit)
            } else {
                Result.Error(DataError.Network.UNKNOWN)
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) {
                "updateStyle failed styleId=${style.id}"
            }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    override suspend fun deleteStyle(
        userId: String,
        location: StyleLocation,
        style: Style
    ): EmptyResult<DataError.Network> =
        try {
            // Remove only the Firestore doc. We intentionally do NOT delete the
            // storage object here: copy/move share the same image across customers,
            // and there's no cheap, race-free way to know at delete time whether
            // another doc still references it (the share state is written async, so
            // an in-memory flag can be stale). Deleting storage could orphan a live
            // copy's image. Image cleanup is deferred to the orphan-sweep backlog;
            // an unreferenced object is wasted bytes, never a broken image.
            uploadOutbox.cancel(uploadJobId(style.photoStoragePath))
            val accepted = offlineWrites.enqueue("deleteStyle styleId=${style.id}") {
                collectionFor(userId, location)
                    .document(style.id)
                    .delete()
            }
            if (!accepted) {
                return Result.Error(DataError.Network.UNKNOWN)
            }
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) {
                "deleteStyle failed styleId=${style.id}"
            }
            Result.Error(DataError.Network.UNKNOWN)
        }

    override suspend fun copyStyle(
        userId: String,
        from: StyleLocation,
        style: Style,
        to: StyleLocation,
    ): EmptyResult<DataError.Network> =
        try {
            writeSharedCopy(userId, to, style)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "copyStyle failed styleId=${style.id}" }
            Result.Error(DataError.Network.UNKNOWN)
        }

    override suspend fun moveStyle(
        userId: String,
        from: StyleLocation,
        style: Style,
        to: StyleLocation,
    ): EmptyResult<DataError.Network> =
        try {
            // Create the target (sharing the image) then remove the source doc only —
            // the image stays put for whichever doc now references it.
            when (val created = writeSharedCopy(userId, to, style)) {
                is Result.Error -> created
                is Result.Success -> {
                    // The copy is committed; removing the source is a fire-and-forget
                    // background write like every other offline mutation (failures
                    // surface via logs + snapshot reconciliation). Don't report the
                    // whole move as failed if this second enqueue's scheduling boolean
                    // comes back false — the copy already succeeded, so that would
                    // leave the style on both customers behind a misleading error.
                    offlineWrites.enqueue("moveStyle deleteSource styleId=${style.id}") {
                        collectionFor(userId, from)
                            .document(style.id)
                            .delete()
                    }
                    Result.Success(Unit)
                }
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "moveStyle failed styleId=${style.id}" }
            Result.Error(DataError.Network.UNKNOWN)
        }

    /**
     * Write a style for [to] location that shows [source]'s image.
     *
     * - If the source image is already in storage (has a [Style.photoUrl]), the
     *   new doc just points at the same URL/path — no re-upload, marked SYNCED.
     *   The shared object is never eagerly deleted (see [deleteStyle]).
     * - If the source is still uploading (no photoUrl yet) we re-upload an
     *   independent copy from its local bytes. Otherwise the new doc would be
     *   saved with an empty URL that never fills in — the source's upload only
     *   patches the source doc, not this copy — leaving a blank image.
     */
    private suspend fun writeSharedCopy(
        userId: String,
        to: StyleLocation,
        source: Style,
    ): EmptyResult<DataError.Network> {
        if (source.photoUrl.isBlank()) {
            return reuploadIndependentCopy(userId, to, source)
        }
        val docRef = collectionFor(userId, to).document
        val now = Clock.System.now().toEpochMilliseconds()
        val copy = source.copy(
            id = docRef.id,
            customerId = (to as? StyleLocation.CustomerCloset)?.customerId.orEmpty(),
            syncState = ImageSyncState.SYNCED,
            localPhotoPath = null,
            createdAt = now,
            updatedAt = now,
        )
        val accepted = offlineWrites.enqueue("writeSharedCopy styleId=${docRef.id} from=${source.id}") {
            docRef.set(copy.toStyleDto())
        }
        return if (accepted) Result.Success(Unit) else Result.Error(DataError.Network.UNKNOWN)
    }

    @Suppress("ReturnCount")
    private suspend fun reuploadIndependentCopy(
        userId: String,
        to: StyleLocation,
        source: Style,
    ): EmptyResult<DataError.Network> {
        val localPath = source.localPhotoPath
            ?: uploadOutbox.localPathForStoragePath(source.photoStoragePath)
            ?: return Result.Error(DataError.Network.UNKNOWN)
        val bytes = runCatching { photoStore.read(localPath) }.getOrNull()
            ?: return Result.Error(DataError.Network.UNKNOWN)
        return when (val result = createStyle(userId, to, source.description, bytes)) {
            is Result.Success -> Result.Success(Unit)
            is Result.Error -> Result.Error(result.error)
        }
    }
}
