package com.danzucker.stitchpad.feature.style.data

import com.danzucker.stitchpad.core.data.dto.StyleDto
import com.danzucker.stitchpad.core.data.mapper.toStyle
import com.danzucker.stitchpad.core.data.mapper.toStyleDto
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.ImageSyncState
import com.danzucker.stitchpad.core.domain.model.Style
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

class FirebaseStyleRepository(
    private val firestore: FirebaseFirestore,
    private val offlineWrites: OfflineWriteDispatcher,
    private val photoStore: OfflinePhotoStore,
    private val uploadOutbox: OfflineUploadOutbox,
) : StyleRepository {

    private fun stylesCollection(userId: String, customerId: String) =
        firestore.collection("users")
            .document(userId)
            .collection("customers")
            .document(customerId)
            .collection("styles")

    private fun storagePath(userId: String, customerId: String, styleId: String): String =
        "users/$userId/customers/$customerId/styles/$styleId.jpg"

    private fun uploadJobId(path: String): String =
        "${OfflineUploadJobType.STYLE_GALLERY_IMAGE}:$path"

    private fun Style.withLocalPendingPhoto(): Style =
        if (syncState == ImageSyncState.PENDING) {
            copy(localPhotoPath = uploadOutbox.localPathForStoragePath(photoStoragePath))
        } else {
            this
        }

    override fun observeStyles(
        userId: String,
        customerId: String
    ): Flow<Result<List<Style>, DataError.Network>> =
        stylesCollection(userId, customerId)
            .snapshots()
            .map<_, Result<List<Style>, DataError.Network>> { snapshot ->
                val styles = snapshot.documents
                    .mapNotNull { doc ->
                        runCatching {
                            doc.data<StyleDto>().toStyle(customerId).withLocalPendingPhoto()
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
        customerId: String,
        description: String,
        photoBytes: ByteArray
    ): Result<String, DataError.Network> {
        val docRef = stylesCollection(userId, customerId).document
        val path = storagePath(userId, customerId, docRef.id)
        return try {
            val localPath = photoStore.save(photoBytes, "style-${docRef.id}.jpg")
            val style = Style(
                id = docRef.id,
                customerId = customerId,
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
                    customerId = customerId,
                    styleId = docRef.id,
                    storagePath = path,
                    localPath = localPath,
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
        customerId: String,
        description: String,
        photoBytesList: List<ByteArray>,
    ): Result<List<String>, DataError.Network> {
        if (photoBytesList.isEmpty()) return Result.Success(emptyList())
        val createdIds = mutableListOf<String>()
        photoBytesList.forEach { bytes ->
            when (val result = createStyle(userId, customerId, description, bytes)) {
                is Result.Success -> createdIds += result.data
                is Result.Error -> return Result.Error(result.error)
            }
        }
        return Result.Success(createdIds)
    }

    override suspend fun updateStyle(
        userId: String,
        customerId: String,
        style: Style,
        newPhotoBytes: ByteArray?
    ): EmptyResult<DataError.Network> {
        return try {
            val accepted = if (newPhotoBytes != null) {
                val path = style.photoStoragePath.ifBlank {
                    storagePath(userId, customerId, style.id)
                }
                val localPath = photoStore.save(newPhotoBytes, "style-${style.id}.jpg")
                val pendingStyle = style.copy(
                    photoStoragePath = path,
                    syncState = ImageSyncState.PENDING,
                    localPhotoPath = localPath,
                )
                val accepted = offlineWrites.enqueue("updateStylePhoto styleId=${style.id}") {
                    stylesCollection(userId, customerId)
                        .document(pendingStyle.id)
                        .set(pendingStyle.toStyleDto())
                }
                if (accepted) {
                    uploadOutbox.enqueue(
                        OfflineUploadJob(
                            id = uploadJobId(path),
                            type = OfflineUploadJobType.STYLE_GALLERY_IMAGE,
                            userId = userId,
                            customerId = customerId,
                            styleId = style.id,
                            storagePath = path,
                            localPath = localPath,
                        )
                    )
                }
                accepted
            } else {
                offlineWrites.enqueue("updateStyle styleId=${style.id}") {
                    stylesCollection(userId, customerId)
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
        customerId: String,
        style: Style
    ): EmptyResult<DataError.Network> =
        try {
            // Only the sole owner cleans up storage. A shared style (copied/moved
            // across customers) leaves the object in place so the copy elsewhere
            // keeps rendering — we only remove this Firestore doc.
            if (style.photoStoragePath.isNotBlank() && !style.sharesImage) {
                uploadOutbox.cancel(uploadJobId(style.photoStoragePath))
                runCatching {
                    uploadOutbox.enqueue(
                        OfflineUploadJob(
                            id = "${OfflineUploadJobType.STORAGE_DELETE}:${style.photoStoragePath}",
                            type = OfflineUploadJobType.STORAGE_DELETE,
                            userId = userId,
                            storagePath = style.photoStoragePath,
                        )
                    )
                }
            }
            val accepted = offlineWrites.enqueue("deleteStyle styleId=${style.id}") {
                stylesCollection(userId, customerId)
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
        fromCustomerId: String,
        style: Style,
        toCustomerId: String,
    ): EmptyResult<DataError.Network> =
        try {
            // Flag the source as sharing too, so deleting it later won't drop the
            // image the new copy still points at.
            val markedSource = offlineWrites.enqueue("markShared styleId=${style.id}") {
                stylesCollection(userId, fromCustomerId)
                    .document(style.id)
                    .set(style.copy(sharesImage = true).toStyleDto())
            }
            if (!markedSource) {
                Result.Error(DataError.Network.UNKNOWN)
            } else {
                writeSharedCopy(userId, toCustomerId, style)
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "copyStyle failed styleId=${style.id}" }
            Result.Error(DataError.Network.UNKNOWN)
        }

    override suspend fun moveStyle(
        userId: String,
        fromCustomerId: String,
        style: Style,
        toCustomerId: String,
    ): EmptyResult<DataError.Network> =
        try {
            // Create the target (sharing the image), preserving any existing share
            // state, then remove the source doc only — the image stays put.
            when (val created = writeSharedCopy(userId, toCustomerId, style, share = style.sharesImage)) {
                is Result.Error -> created
                is Result.Success -> {
                    val deleted = offlineWrites.enqueue("moveStyle deleteSource styleId=${style.id}") {
                        stylesCollection(userId, fromCustomerId)
                            .document(style.id)
                            .delete()
                    }
                    if (deleted) Result.Success(Unit) else Result.Error(DataError.Network.UNKNOWN)
                }
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "moveStyle failed styleId=${style.id}" }
            Result.Error(DataError.Network.UNKNOWN)
        }

    /**
     * Write a new style doc under [toCustomerId] that points at [source]'s image
     * (same photoUrl / storage path — no re-upload). [share] flags whether the
     * new doc shares its image; copy passes true, move preserves the source's flag.
     */
    private suspend fun writeSharedCopy(
        userId: String,
        toCustomerId: String,
        source: Style,
        share: Boolean = true,
    ): EmptyResult<DataError.Network> {
        val docRef = stylesCollection(userId, toCustomerId).document
        val now = Clock.System.now().toEpochMilliseconds()
        val copy = source.copy(
            id = docRef.id,
            customerId = toCustomerId,
            sharesImage = share,
            createdAt = now,
            updatedAt = now,
        )
        val accepted = offlineWrites.enqueue("writeSharedCopy styleId=${docRef.id} from=${source.id}") {
            docRef.set(copy.toStyleDto())
        }
        return if (accepted) Result.Success(Unit) else Result.Error(DataError.Network.UNKNOWN)
    }
}
