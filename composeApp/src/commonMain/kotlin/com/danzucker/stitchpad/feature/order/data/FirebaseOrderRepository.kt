package com.danzucker.stitchpad.feature.order.data

import com.danzucker.stitchpad.core.data.mapper.toOrder
import com.danzucker.stitchpad.core.data.mapper.toOrderDto
import com.danzucker.stitchpad.core.data.mapper.toPaymentDto
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.ImageSyncState
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus
import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.offline.OfflinePhotoStore
import com.danzucker.stitchpad.core.offline.OfflineUploadJob
import com.danzucker.stitchpad.core.offline.OfflineUploadJobType
import com.danzucker.stitchpad.core.offline.OfflineUploadOutbox
import com.danzucker.stitchpad.core.offline.OfflineWriteDispatcher
import com.danzucker.stitchpad.feature.style.data.toStorageData
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val TAG = "OrderRepo"

internal fun applyCompletedOrderUploadPatches(
    order: Order,
    completedUrlForStoragePath: (String) -> String?,
): Order = order.copy(
    items = order.items.map { item ->
        item.copy(
            styleImages = item.styleImages.map { ref ->
                val storagePath = ref.photoStoragePath
                val completedUrl = storagePath?.let(completedUrlForStoragePath)
                if (completedUrl != null) {
                    ref.copy(
                        photoUrl = completedUrl,
                        syncState = ImageSyncState.SYNCED,
                        localPhotoPath = null,
                    )
                } else {
                    ref
                }
            },
            fabricImages = item.fabricImages.map { ref ->
                val completedUrl = completedUrlForStoragePath(ref.photoStoragePath)
                if (completedUrl != null) {
                    ref.copy(
                        photoUrl = completedUrl,
                        syncState = ImageSyncState.SYNCED,
                        localPhotoPath = null,
                    )
                } else {
                    ref
                }
            },
        )
    },
)

@Suppress("TooManyFunctions")
class FirebaseOrderRepository(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val offlineWrites: OfflineWriteDispatcher,
    private val photoStore: OfflinePhotoStore,
    private val uploadOutbox: OfflineUploadOutbox,
) : OrderRepository {

    private fun ordersCollection(userId: String) =
        firestore.collection("users").document(userId).collection("orders")

    private fun fabricStoragePath(userId: String, orderId: String, itemId: String): String =
        "users/$userId/orders/$orderId/fabrics/$itemId.jpg"

    /**
     * Multi-image path scheme. Uses a unique suffix per upload (not a positional
     * index) so that appending images to an order that already has saved images
     * never collides with their storage paths. codex P1 — without this, editing
     * an order with N saved images and adding one more would overwrite the
     * existing first image at `$itemId-0.jpg`.
     */
    private fun fabricStoragePath(userId: String, orderId: String, itemId: String, suffix: String): String =
        "users/$userId/orders/$orderId/fabrics/$itemId-$suffix.jpg"

    private fun styleStoragePath(userId: String, orderId: String, itemId: String): String =
        "users/$userId/orders/$orderId/styles/$itemId.jpg"

    /** See [fabricStoragePath] — same rationale for unique-suffix scheme. */
    private fun styleStoragePath(userId: String, orderId: String, itemId: String, suffix: String): String =
        "users/$userId/orders/$orderId/styles/$itemId-$suffix.jpg"

    private fun uploadJobId(type: OfflineUploadJobType, path: String): String =
        "$type:$path"

    private fun Order.withLocalPendingImages(): Order = copy(
        items = items.map { item ->
            item.copy(
                styleImages = item.styleImages.map { ref ->
                    if (ref.syncState == ImageSyncState.PENDING) {
                        ref.copy(
                            localPhotoPath = ref.photoStoragePath
                                ?.let(uploadOutbox::localPathForStoragePath)
                        )
                    } else {
                        ref
                    }
                },
                fabricImages = item.fabricImages.map { ref ->
                    if (ref.syncState == ImageSyncState.PENDING) {
                        ref.copy(localPhotoPath = uploadOutbox.localPathForStoragePath(ref.photoStoragePath))
                    } else {
                        ref
                    }
                },
            )
        },
    )

    private fun Order.withCompletedUploadPatches(): Order = copy(
        items = applyCompletedOrderUploadPatches(
            order = this,
            completedUrlForStoragePath = uploadOutbox::completedUrlForStoragePath,
        ).items,
    )

    override fun observeOrders(userId: String): Flow<Result<List<Order>, DataError.Network>> =
        ordersCollection(userId)
            .snapshots()
            .map { snapshot ->
                val orders = snapshot.documents
                    .mapNotNull { doc ->
                        runCatching {
                            doc.data<com.danzucker.stitchpad.core.data.dto.OrderDto>()
                                .toOrder(userId)
                                .withLocalPendingImages()
                        }.getOrNull()
                    }
                    // Filter archived orders client-side. The GitLive Firebase
                    // SDK doesn't support `whereEqualTo(field, null)` cleanly
                    // across platforms, and the per-user dataset is small
                    // enough (< 1k orders) that client-side filtering is fine.
                    .filter { it.archivedAt == null }
                Result.Success(orders) as Result<List<Order>, DataError.Network>
            }
            .catch { throwable ->
                AppLogger.e(tag = TAG, throwable = throwable) { "observeOrders failed" }
                emit(Result.Error(DataError.Network.UNKNOWN))
            }

    override fun observeOrder(
        userId: String,
        orderId: String
    ): Flow<Result<Order, DataError.Network>> =
        ordersCollection(userId).document(orderId)
            .snapshots
            .map { snapshot ->
                if (!snapshot.exists) {
                    Result.Error(DataError.Network.NOT_FOUND) as Result<Order, DataError.Network>
                } else {
                    val dto = snapshot.data<com.danzucker.stitchpad.core.data.dto.OrderDto>()
                    Result.Success(dto.toOrder(userId).withLocalPendingImages()) as Result<Order, DataError.Network>
                }
            }
            .catch { throwable ->
                AppLogger.e(tag = TAG, throwable = throwable) { "observeOrder failed orderId=$orderId" }
                emit(Result.Error(DataError.Network.UNKNOWN))
            }

    override suspend fun getOrder(
        userId: String,
        orderId: String
    ): Result<Order, DataError.Network> {
        return try {
            val doc = ordersCollection(userId).document(orderId).get()
            if (!doc.exists) return Result.Error(DataError.Network.NOT_FOUND)
            val dto = doc.data<com.danzucker.stitchpad.core.data.dto.OrderDto>()
            Result.Success(dto.toOrder(userId).withLocalPendingImages())
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "getOrder failed orderId=$orderId" }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    override suspend fun createOrder(
        userId: String,
        order: Order
    ): EmptyResult<DataError.Network> {
        val docRef = if (order.id.isBlank()) {
            ordersCollection(userId).document
        } else {
            ordersCollection(userId).document(order.id)
        }
        val dto = order.withCompletedUploadPatches().toOrderDto().copy(id = docRef.id)
        val accepted = offlineWrites.enqueue("createOrder orderId=${docRef.id}") {
            docRef.set(dto)
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        return Result.Success(Unit)
    }

    override suspend fun updateOrder(
        userId: String,
        order: Order
    ): EmptyResult<DataError.Network> {
        val accepted = offlineWrites.enqueue("updateOrder orderId=${order.id}") {
            ordersCollection(userId).document(order.id).set(order.withCompletedUploadPatches().toOrderDto())
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        return Result.Success(Unit)
    }

    override suspend fun updateOrderStatus(
        userId: String,
        orderId: String,
        newStatus: OrderStatus
    ): EmptyResult<DataError.Network> {
        val now = Clock.System.now().toEpochMilliseconds()
        val change = com.danzucker.stitchpad.core.data.dto.StatusChangeDto(status = newStatus.name, changedAt = now)
        val accepted = offlineWrites.enqueue("updateOrderStatus orderId=$orderId") {
            ordersCollection(userId).document(orderId).update(
                "status" to newStatus.name,
                "statusHistory" to FieldValue.arrayUnion(change),
                "updatedAt" to now,
            )
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        return Result.Success(Unit)
    }

    override suspend fun recordPayment(
        userId: String,
        orderId: String,
        payment: Payment,
    ): EmptyResult<DataError.Network> {
        val now = Clock.System.now().toEpochMilliseconds()
        val stampedPayment = payment.copy(recordedAt = payment.recordedAt.takeIf { it > 0L } ?: now)
        val accepted = offlineWrites.enqueue("recordPayment orderId=$orderId paymentId=${payment.id}") {
            ordersCollection(userId).document(orderId).update(
                "payments" to FieldValue.arrayUnion(stampedPayment.toPaymentDto()),
                "updatedAt" to now,
            )
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        return Result.Success(Unit)
    }

    override suspend fun updateSubStatus(
        userId: String,
        orderId: String,
        subStatus: OrderSubStatus?,
    ): EmptyResult<DataError.Network> {
        val now = Clock.System.now().toEpochMilliseconds()
        val accepted = offlineWrites.enqueue("updateSubStatus orderId=$orderId") {
            ordersCollection(userId).document(orderId).update(
                "subStatus" to (subStatus?.name ?: FieldValue.delete),
                "updatedAt" to now,
            )
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        return Result.Success(Unit)
    }

    override suspend fun updateNotes(
        userId: String,
        orderId: String,
        notes: String?,
    ): EmptyResult<DataError.Network> {
        val now = Clock.System.now().toEpochMilliseconds()
        val accepted = offlineWrites.enqueue("updateNotes orderId=$orderId") {
            ordersCollection(userId).document(orderId).update(
                "notes" to (notes ?: FieldValue.delete),
                "updatedAt" to now,
            )
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        return Result.Success(Unit)
    }

    override suspend fun archiveOrder(
        userId: String,
        orderId: String,
    ): EmptyResult<DataError.Network> {
        val now = Clock.System.now().toEpochMilliseconds()
        val accepted = offlineWrites.enqueue("archiveOrder orderId=$orderId") {
            ordersCollection(userId).document(orderId).update(
                "archivedAt" to now,
                "updatedAt" to now,
            )
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        return Result.Success(Unit)
    }

    override suspend fun deleteOrder(
        userId: String,
        orderId: String
    ): EmptyResult<DataError.Network> {
        val accepted = offlineWrites.enqueue("deleteOrder orderId=$orderId") {
            ordersCollection(userId).document(orderId).delete()
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        return Result.Success(Unit)
    }

    private suspend fun deleteOrderImagePath(storagePath: String) {
        uploadOutbox.cancel(uploadJobId(OfflineUploadJobType.ORDER_FABRIC_IMAGE, storagePath))
        uploadOutbox.cancel(uploadJobId(OfflineUploadJobType.ORDER_STYLE_IMAGE, storagePath))
        uploadOutbox.enqueue(
            OfflineUploadJob(
                id = uploadJobId(OfflineUploadJobType.STORAGE_DELETE, storagePath),
                type = OfflineUploadJobType.STORAGE_DELETE,
                userId = "",
                storagePath = storagePath,
            )
        )
    }

    override fun newOrderId(userId: String): String =
        ordersCollection(userId).document.id

    override suspend fun uploadFabricPhoto(
        userId: String,
        orderId: String,
        itemId: String,
        photoBytes: ByteArray
    ): Result<Pair<String, String>, DataError.Network> {
        val path = fabricStoragePath(userId, orderId, itemId)
        return try {
            storage.reference.child(path).putData(photoBytes.toStorageData())
            val downloadUrl = storage.reference.child(path).getDownloadUrl()
            Result.Success(downloadUrl to path)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "uploadFabricPhoto failed itemId=$itemId" }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    override suspend fun uploadStylePhoto(
        userId: String,
        orderId: String,
        itemId: String,
        photoBytes: ByteArray
    ): Result<Pair<String, String>, DataError.Network> {
        val path = styleStoragePath(userId, orderId, itemId)
        return try {
            storage.reference.child(path).putData(photoBytes.toStorageData())
            val downloadUrl = storage.reference.child(path).getDownloadUrl()
            Result.Success(downloadUrl to path)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "uploadStylePhoto failed itemId=$itemId" }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Suppress("ReturnCount")
    override suspend fun uploadFabricPhotos(
        userId: String,
        orderId: String,
        itemId: String,
        photoBytesList: List<ByteArray>,
    ): Result<List<Pair<String, String>>, DataError.Network> {
        return uploadPhotos(
            userId = userId,
            orderId = orderId,
            itemId = itemId,
            photoBytesList = photoBytesList,
            operationName = "uploadFabricPhotos",
            jobType = OfflineUploadJobType.ORDER_FABRIC_IMAGE,
        ) { suffix ->
            fabricStoragePath(userId, orderId, itemId, suffix)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Suppress("ReturnCount")
    override suspend fun uploadStylePhotos(
        userId: String,
        orderId: String,
        itemId: String,
        photoBytesList: List<ByteArray>,
    ): Result<List<Pair<String, String>>, DataError.Network> {
        return uploadPhotos(
            userId = userId,
            orderId = orderId,
            itemId = itemId,
            photoBytesList = photoBytesList,
            operationName = "uploadStylePhotos",
            jobType = OfflineUploadJobType.ORDER_STYLE_IMAGE,
        ) { suffix ->
            styleStoragePath(userId, orderId, itemId, suffix)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Suppress("ReturnCount")
    private suspend fun uploadPhotos(
        userId: String,
        orderId: String,
        itemId: String,
        photoBytesList: List<ByteArray>,
        operationName: String,
        jobType: OfflineUploadJobType,
        storagePath: (suffix: String) -> String,
    ): Result<List<Pair<String, String>>, DataError.Network> {
        if (photoBytesList.isEmpty()) return Result.Success(emptyList())
        val results = mutableListOf<Pair<String, String>>()
        photoBytesList.forEach { bytes ->
            // Unique suffix per upload — see fabricStoragePath() docs. Positional
            // index would collide when editing an order that already has saved
            // images and appending one more.
            val suffix = Uuid.random().toString().take(8)
            val path = storagePath(suffix)
            try {
                val localPath = photoStore.save(bytes, "$operationName-$suffix.jpg")
                results += localPath to path
                uploadOutbox.enqueue(
                    OfflineUploadJob(
                        id = uploadJobId(jobType, path),
                        type = jobType,
                        userId = userId,
                        storagePath = path,
                        localPath = localPath,
                        orderId = orderId,
                        itemId = itemId,
                    )
                )
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                AppLogger.e(tag = TAG, throwable = e) { "$operationName failed itemId=$itemId suffix=$suffix" }
                return Result.Error(DataError.Network.UNKNOWN)
            }
        }
        return Result.Success(results)
    }

    suspend fun deleteFabricPhoto(storagePath: String) {
        runCatching {
            deleteOrderImagePath(storagePath)
        }
            .onFailure { throwable ->
                AppLogger.w(tag = TAG, throwable = throwable) { "deleteFabricPhoto failed" }
            }
    }

    override suspend fun deleteStoragePaths(
        paths: List<String>,
    ): EmptyResult<DataError.Network> {
        paths.filter { it.isNotBlank() }.forEach { path ->
            runCatching {
                deleteOrderImagePath(path)
            }
        }
        return Result.Success(Unit)
    }
}
