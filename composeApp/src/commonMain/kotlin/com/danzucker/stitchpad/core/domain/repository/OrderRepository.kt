package com.danzucker.stitchpad.core.domain.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import kotlinx.coroutines.flow.Flow

interface OrderRepository {
    fun observeOrders(userId: String): Flow<Result<List<Order>, DataError.Network>>
    suspend fun getOrder(userId: String, orderId: String): Result<Order, DataError.Network>
    suspend fun createOrder(userId: String, order: Order): EmptyResult<DataError.Network>
    suspend fun updateOrder(userId: String, order: Order): EmptyResult<DataError.Network>
    suspend fun updateOrderStatus(
        userId: String,
        orderId: String,
        newStatus: OrderStatus
    ): EmptyResult<DataError.Network>
    suspend fun deleteOrder(userId: String, orderId: String): EmptyResult<DataError.Network>

    fun newOrderId(userId: String): String

    suspend fun uploadFabricPhoto(
        userId: String,
        orderId: String,
        itemId: String,
        photoBytes: ByteArray
    ): Result<Pair<String, String>, DataError.Network>
}
