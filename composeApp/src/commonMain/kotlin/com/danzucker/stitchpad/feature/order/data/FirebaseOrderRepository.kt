package com.danzucker.stitchpad.feature.order.data

import com.danzucker.stitchpad.core.data.dto.OrderDto
import com.danzucker.stitchpad.core.data.dto.StatusChangeDto
import com.danzucker.stitchpad.core.data.mapper.migrateLegacyDeposit
import com.danzucker.stitchpad.core.data.mapper.toOrder
import com.danzucker.stitchpad.core.data.mapper.toOrderDto
import com.danzucker.stitchpad.core.data.mapper.toPaymentDto
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus
import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.model.StyleImageSource
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.style.data.toStorageData
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

private const val TAG = "OrderRepo"

@Suppress("TooManyFunctions")
class FirebaseOrderRepository(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) : OrderRepository {

    private fun ordersCollection(userId: String) =
        firestore.collection("users").document(userId).collection("orders")

    private fun fabricStoragePath(userId: String, orderId: String, itemId: String): String =
        "users/$userId/orders/$orderId/fabrics/$itemId.jpg"

    private fun fabricStoragePath(userId: String, orderId: String, itemId: String, index: Int): String =
        "users/$userId/orders/$orderId/fabrics/$itemId-$index.jpg"

    private fun styleStoragePath(userId: String, orderId: String, itemId: String): String =
        "users/$userId/orders/$orderId/styles/$itemId.jpg"

    private fun styleStoragePath(userId: String, orderId: String, itemId: String, index: Int): String =
        "users/$userId/orders/$orderId/styles/$itemId-$index.jpg"

    override fun observeOrders(userId: String): Flow<Result<List<Order>, DataError.Network>> =
        ordersCollection(userId)
            .snapshots()
            .map { snapshot ->
                val orders = snapshot.documents
                    .mapNotNull { doc ->
                        runCatching { doc.data<OrderDto>().toOrder(userId) }.getOrNull()
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
                    val dto = snapshot.data<OrderDto>()
                    Result.Success(dto.toOrder(userId)) as Result<Order, DataError.Network>
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
            val dto = doc.data<OrderDto>()
            Result.Success(dto.toOrder(userId))
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
        return try {
            val dto = order.toOrderDto().copy(id = docRef.id)
            docRef.set(dto)
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "createOrder failed orderId=${docRef.id}" }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    override suspend fun updateOrder(
        userId: String,
        order: Order
    ): EmptyResult<DataError.Network> {
        return try {
            ordersCollection(userId).document(order.id).set(order.toOrderDto())
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "updateOrder failed orderId=${order.id}" }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    override suspend fun updateOrderStatus(
        userId: String,
        orderId: String,
        newStatus: OrderStatus
    ): EmptyResult<DataError.Network> {
        return try {
            val docRef = ordersCollection(userId).document(orderId)
            // Capture once outside the transaction body — Firestore retries on
            // concurrent modification, and we want a stable timestamp tied to
            // when the user took the action, not to which retry attempt won.
            val now = Clock.System.now().toEpochMilliseconds()
            val notFound = firestore.runTransaction {
                val snap = get(docRef)
                if (!snap.exists) return@runTransaction true
                val dto = snap.data<OrderDto>()
                val updatedDto = dto.copy(
                    status = newStatus.name,
                    statusHistory = dto.statusHistory + StatusChangeDto(
                        status = newStatus.name,
                        changedAt = now
                    ),
                    updatedAt = now
                )
                set(docRef, updatedDto)
                false
            }
            if (notFound) Result.Error(DataError.Network.NOT_FOUND) else Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "updateOrderStatus failed orderId=$orderId" }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    override suspend fun recordPayment(
        userId: String,
        orderId: String,
        payment: Payment,
    ): EmptyResult<DataError.Network> {
        return try {
            val docRef = ordersCollection(userId).document(orderId)
            // Capture once outside runTransaction so retries don't shift the
            // recordedAt/updatedAt timestamps — the value reflects when the
            // user tapped Save, not which retry won the race.
            val now = Clock.System.now().toEpochMilliseconds()
            val stampedPayment = payment.copy(recordedAt = now)
            val notFound = firestore.runTransaction {
                val snap = get(docRef)
                if (!snap.exists) return@runTransaction true
                val dto = snap.data<OrderDto>()
                // Absorb any legacy depositPaid into the payments list BEFORE
                // appending the new one — otherwise zeroing depositPaid below
                // permanently drops the legacy deposit.
                val migratedPayments = migrateLegacyDeposit(
                    payments = dto.payments,
                    depositPaid = dto.depositPaid,
                    createdAt = dto.createdAt,
                )
                val updatedDto = dto.copy(
                    payments = migratedPayments + stampedPayment.toPaymentDto(),
                    depositPaid = 0.0,
                    updatedAt = now,
                )
                set(docRef, updatedDto)
                false
            }
            if (notFound) Result.Error(DataError.Network.NOT_FOUND) else Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "recordPayment failed orderId=$orderId" }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    override suspend fun updateSubStatus(
        userId: String,
        orderId: String,
        subStatus: OrderSubStatus?,
    ): EmptyResult<DataError.Network> {
        return try {
            val docRef = ordersCollection(userId).document(orderId)
            val now = Clock.System.now().toEpochMilliseconds()
            val notFound = firestore.runTransaction {
                val snap = get(docRef)
                if (!snap.exists) return@runTransaction true
                val dto = snap.data<OrderDto>()
                set(docRef, dto.copy(subStatus = subStatus?.name, updatedAt = now))
                false
            }
            if (notFound) Result.Error(DataError.Network.NOT_FOUND) else Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "updateSubStatus failed orderId=$orderId" }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    override suspend fun updateNotes(
        userId: String,
        orderId: String,
        notes: String?,
    ): EmptyResult<DataError.Network> {
        return try {
            val docRef = ordersCollection(userId).document(orderId)
            val now = Clock.System.now().toEpochMilliseconds()
            val notFound = firestore.runTransaction {
                val snap = get(docRef)
                if (!snap.exists) return@runTransaction true
                val dto = snap.data<OrderDto>()
                set(docRef, dto.copy(notes = notes, updatedAt = now))
                false
            }
            if (notFound) Result.Error(DataError.Network.NOT_FOUND) else Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "updateNotes failed orderId=$orderId" }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    override suspend fun archiveOrder(
        userId: String,
        orderId: String,
    ): EmptyResult<DataError.Network> {
        return try {
            val docRef = ordersCollection(userId).document(orderId)
            val now = Clock.System.now().toEpochMilliseconds()
            val notFound = firestore.runTransaction {
                val snap = get(docRef)
                if (!snap.exists) return@runTransaction true
                val dto = snap.data<OrderDto>()
                set(docRef, dto.copy(archivedAt = now, updatedAt = now))
                false
            }
            if (notFound) Result.Error(DataError.Network.NOT_FOUND) else Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "archiveOrder failed orderId=$orderId" }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    override suspend fun deleteOrder(
        userId: String,
        orderId: String
    ): EmptyResult<DataError.Network> {
        return try {
            deleteFabricPhotosFor(userId, orderId)
            ordersCollection(userId).document(orderId).delete()
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "deleteOrder failed orderId=$orderId" }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    private suspend fun deleteFabricPhotosFor(userId: String, orderId: String) {
        val doc = ordersCollection(userId).document(orderId).get()
        if (!doc.exists) return
        val dto = doc.data<OrderDto>()
        dto.items.forEach { item ->
            val path = item.fabricPhotoStoragePath
            if (!path.isNullOrBlank()) {
                runCatching { storage.reference.child(path).delete() }
            }
            val stylePath = item.stylePhotoStoragePath
            if (!stylePath.isNullOrBlank()) {
                runCatching { storage.reference.child(stylePath).delete() }
            }
            // PTSP-11 multi-image cleanup
            item.fabricImages.forEach { ref ->
                val p = ref.photoStoragePath
                if (p.isNotBlank()) {
                    runCatching { storage.reference.child(p).delete() }
                }
            }
            item.styleImages
                .filter { it.source == StyleImageSource.UPLOADED.name }
                .forEach { ref ->
                    val p = ref.photoStoragePath
                    if (!p.isNullOrBlank()) {
                        runCatching { storage.reference.child(p).delete() }
                    }
                }
        }
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

    @Suppress("ReturnCount")
    override suspend fun uploadFabricPhotos(
        userId: String,
        orderId: String,
        itemId: String,
        photoBytesList: List<ByteArray>,
    ): Result<List<Pair<String, String>>, DataError.Network> {
        if (photoBytesList.isEmpty()) return Result.Success(emptyList())
        val results = mutableListOf<Pair<String, String>>()
        photoBytesList.forEachIndexed { index, bytes ->
            val path = fabricStoragePath(userId, orderId, itemId, index)
            try {
                storage.reference.child(path).putData(bytes.toStorageData())
                val downloadUrl = storage.reference.child(path).getDownloadUrl()
                results += downloadUrl to path
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                AppLogger.e(tag = TAG, throwable = e) { "uploadFabricPhotos failed itemId=$itemId index=$index" }
                return Result.Error(DataError.Network.UNKNOWN)
            }
        }
        return Result.Success(results)
    }

    @Suppress("ReturnCount")
    override suspend fun uploadStylePhotos(
        userId: String,
        orderId: String,
        itemId: String,
        photoBytesList: List<ByteArray>,
    ): Result<List<Pair<String, String>>, DataError.Network> {
        if (photoBytesList.isEmpty()) return Result.Success(emptyList())
        val results = mutableListOf<Pair<String, String>>()
        photoBytesList.forEachIndexed { index, bytes ->
            val path = styleStoragePath(userId, orderId, itemId, index)
            try {
                storage.reference.child(path).putData(bytes.toStorageData())
                val downloadUrl = storage.reference.child(path).getDownloadUrl()
                results += downloadUrl to path
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                AppLogger.e(tag = TAG, throwable = e) { "uploadStylePhotos failed itemId=$itemId index=$index" }
                return Result.Error(DataError.Network.UNKNOWN)
            }
        }
        return Result.Success(results)
    }

    suspend fun deleteFabricPhoto(storagePath: String) {
        runCatching { storage.reference.child(storagePath).delete() }
            .onFailure { throwable ->
                AppLogger.w(tag = TAG, throwable = throwable) { "deleteFabricPhoto failed" }
            }
    }

    override suspend fun deleteStoragePaths(
        paths: List<String>,
    ): EmptyResult<DataError.Network> {
        paths.filter { it.isNotBlank() }.forEach { path ->
            runCatching { storage.reference.child(path).delete() }
        }
        return Result.Success(Unit)
    }
}
