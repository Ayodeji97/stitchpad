package com.danzucker.stitchpad.core.domain.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderCost
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus
import com.danzucker.stitchpad.core.domain.model.Payment
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
interface OrderRepository {
    fun observeOrders(userId: String): Flow<Result<List<Order>, DataError.Network>>

    /** Archived orders only (most-recently-archived first). Excluded from [observeOrders]. */
    fun observeArchivedOrders(userId: String): Flow<Result<List<Order>, DataError.Network>>
    fun observeOrder(userId: String, orderId: String): Flow<Result<Order, DataError.Network>>
    suspend fun getOrder(userId: String, orderId: String): Result<Order, DataError.Network>
    suspend fun createOrder(userId: String, order: Order): EmptyResult<DataError.Network>
    suspend fun updateOrder(userId: String, order: Order): EmptyResult<DataError.Network>
    suspend fun updateOrderStatus(
        userId: String,
        orderId: String,
        newStatus: OrderStatus
    ): EmptyResult<DataError.Network>
    suspend fun deleteOrder(
        userId: String,
        orderId: String,
        ownedStoragePaths: List<String> = emptyList(),
    ): EmptyResult<DataError.Network>

    suspend fun recordPayment(
        userId: String,
        orderId: String,
        payment: Payment,
        knownPayments: List<Payment> = emptyList(),
    ): EmptyResult<DataError.Network>

    suspend fun updateSubStatus(
        userId: String,
        orderId: String,
        subStatus: OrderSubStatus?,
    ): EmptyResult<DataError.Network>

    suspend fun updateNotes(
        userId: String,
        orderId: String,
        notes: String?,
    ): EmptyResult<DataError.Network>

    suspend fun updateCosts(
        userId: String,
        orderId: String,
        costs: List<OrderCost>,
    ): EmptyResult<DataError.Network>

    suspend fun archiveOrder(
        userId: String,
        orderId: String,
    ): EmptyResult<DataError.Network>

    suspend fun unarchiveOrder(
        userId: String,
        orderId: String,
    ): EmptyResult<DataError.Network>

    suspend fun assignOrder(
        userId: String,
        orderId: String,
        memberId: String?,
        memberName: String?,
    ): EmptyResult<DataError.Network>

    /** Items-only base-doc update (detail-screen garment edits: style/fabric photos,
     *  fabric name, measurement link). Never touches /private/money — item PRICES are
     *  not part of this write (they live in the money mirror). Both roles use it. */
    suspend fun updateItems(
        userId: String,
        orderId: String,
        items: List<OrderItem>,
    ): EmptyResult<DataError.Network>

    fun newOrderId(userId: String): String

    suspend fun uploadFabricPhoto(
        userId: String,
        orderId: String,
        itemId: String,
        photoBytes: ByteArray
    ): Result<Pair<String, String>, DataError.Network>

    suspend fun uploadStylePhoto(
        userId: String,
        orderId: String,
        itemId: String,
        photoBytes: ByteArray
    ): Result<Pair<String, String>, DataError.Network>

    suspend fun uploadFabricPhotos(
        userId: String,
        orderId: String,
        itemId: String,
        photoBytesList: List<ByteArray>,
    ): Result<List<Pair<String, String>>, DataError.Network>

    suspend fun uploadStylePhotos(
        userId: String,
        orderId: String,
        itemId: String,
        photoBytesList: List<ByteArray>,
    ): Result<List<Pair<String, String>>, DataError.Network>

    suspend fun deleteStoragePaths(
        paths: List<String>,
    ): EmptyResult<DataError.Network>
}
