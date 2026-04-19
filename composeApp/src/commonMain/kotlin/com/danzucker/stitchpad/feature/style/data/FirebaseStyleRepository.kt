package com.danzucker.stitchpad.feature.style.data

import com.danzucker.stitchpad.core.data.dto.StyleDto
import com.danzucker.stitchpad.core.data.mapper.toStyle
import com.danzucker.stitchpad.core.data.mapper.toStyleDto
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.core.domain.repository.StyleRepository
import com.danzucker.stitchpad.core.logging.AppLogger
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private const val TAG = "StyleRepo"

class FirebaseStyleRepository(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) : StyleRepository {

    private fun stylesCollection(userId: String, customerId: String) =
        firestore.collection("users")
            .document(userId)
            .collection("customers")
            .document(customerId)
            .collection("styles")

    private fun storagePath(userId: String, customerId: String, styleId: String): String =
        "users/$userId/customers/$customerId/styles/$styleId.jpg"

    override fun observeStyles(
        userId: String,
        customerId: String
    ): Flow<Result<List<Style>, DataError.Network>> =
        stylesCollection(userId, customerId)
            .snapshots()
            .map<_, Result<List<Style>, DataError.Network>> { snapshot ->
                val styles = snapshot.documents
                    .mapNotNull { doc ->
                        runCatching { doc.data<StyleDto>().toStyle(customerId) }.getOrNull()
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
    ): EmptyResult<DataError.Network> {
        val docRef = stylesCollection(userId, customerId).document
        val path = storagePath(userId, customerId, docRef.id)
        var uploaded = false
        return try {
            storage.reference.child(path).putData(photoBytes.toStorageData())
            uploaded = true
            val downloadUrl = storage.reference.child(path).getDownloadUrl()
            val style = Style(
                id = docRef.id,
                customerId = customerId,
                description = description,
                photoUrl = downloadUrl,
                photoStoragePath = path,
                createdAt = 0L,
                updatedAt = 0L
            )
            docRef.set(style.toStyleDto())
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) {
                "createStyle failed styleId=${docRef.id}"
            }
            if (uploaded) cleanupOrphanedUpload(path)
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    override suspend fun updateStyle(
        userId: String,
        customerId: String,
        style: Style,
        newPhotoBytes: ByteArray?
    ): EmptyResult<DataError.Network> {
        return try {
            val updatedStyle = if (newPhotoBytes != null) {
                val path = style.photoStoragePath.ifBlank {
                    storagePath(userId, customerId, style.id)
                }
                storage.reference.child(path).putData(newPhotoBytes.toStorageData())
                val downloadUrl = storage.reference.child(path).getDownloadUrl()
                style.copy(photoUrl = downloadUrl, photoStoragePath = path)
            } else {
                style
            }
            stylesCollection(userId, customerId)
                .document(updatedStyle.id)
                .set(updatedStyle.toStyleDto())
            Result.Success(Unit)
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
    ): EmptyResult<DataError.Network> {
        return try {
            if (style.photoStoragePath.isNotBlank()) {
                runCatching { storage.reference.child(style.photoStoragePath).delete() }
            }
            stylesCollection(userId, customerId)
                .document(style.id)
                .delete()
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) {
                "deleteStyle failed styleId=${style.id}"
            }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    private suspend fun cleanupOrphanedUpload(path: String) {
        runCatching { storage.reference.child(path).delete() }
            .onFailure { throwable ->
                AppLogger.w(tag = TAG, throwable = throwable) {
                    "cleanupOrphanedUpload failed"
                }
            }
    }
}
