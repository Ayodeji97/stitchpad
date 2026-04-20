package com.danzucker.stitchpad.feature.order.data

import com.danzucker.stitchpad.core.data.dto.OrderDto
import com.danzucker.stitchpad.core.data.dto.StatusChangeDto
import com.danzucker.stitchpad.core.data.mapper.toOrder
import com.danzucker.stitchpad.core.data.mapper.toOrderDto
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus
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

class FirebaseOrderRepository(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) : OrderRepository {

    private fun ordersCollection(userId: String) =
        firestore.collection("users").document(userId).collection("orders")

    private fun fabricStoragePath(userId: String, orderId: String, itemId: String): String =
        "users/$userId/orders/$orderId/fabrics/$itemId.jpg"

    override fun observeOrders(userId: String): Flow<Result<List<Order>, DataError.Network>> =
        ordersCollection(userId)
            .snapshots()
            .map { snapshot ->
                val orders = snapshot.documents.mapNotNull { doc ->
                    runCatching { doc.data<OrderDto>().toOrder(userId) }.getOrNull()
                }
                Result.Success(orders) as Result<List<Order>, DataError.Network>
            }
            .catch { throwable ->
                AppLogger.e(tag = TAG, throwable = throwable) { "observeOrders failed" }
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
            // Atomic read-modify-write: Firestore retries on concurrent modification,
            // preventing lost status updates when two devices update at the same time.
            val notFound = firestore.runTransaction {
                val snap = get(docRef)
                if (!snap.exists) return@runTransaction true
                val dto = snap.data<OrderDto>()
                val now = Clock.System.now().toEpochMilliseconds()
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

    suspend fun deleteFabricPhoto(storagePath: String) {
        runCatching { storage.reference.child(storagePath).delete() }
            .onFailure { throwable ->
                AppLogger.w(tag = TAG, throwable = throwable) { "deleteFabricPhoto failed" }
            }
    }
}
