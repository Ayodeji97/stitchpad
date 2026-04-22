package com.danzucker.stitchpad.core.data.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeOrderRepository : OrderRepository {
    var shouldReturnError: DataError.Network? = null

    private val ordersFlow = MutableStateFlow<List<Order>>(emptyList())

    var ordersList: List<Order>
        get() = ordersFlow.value
        set(value) { ordersFlow.value = value }

    var lastCreatedOrder: Order? = null
    var lastUpdatedOrder: Order? = null
    var lastDeletedOrderId: String? = null
    var lastStatusUpdate: Pair<String, OrderStatus>? = null
    private var nextIdSuffix = 0

    override fun observeOrders(userId: String): Flow<Result<List<Order>, DataError.Network>> =
        ordersFlow.map { list ->
            shouldReturnError?.let { return@map Result.Error(it) }
            Result.Success(list)
        }

    override fun observeOrder(
        userId: String,
        orderId: String
    ): Flow<Result<Order, DataError.Network>> =
        ordersFlow.map { list ->
            shouldReturnError?.let { return@map Result.Error(it) }
            list.firstOrNull { it.id == orderId }?.let { Result.Success(it) }
                ?: Result.Error(DataError.Network.NOT_FOUND)
        }

    override suspend fun getOrder(
        userId: String,
        orderId: String
    ): Result<Order, DataError.Network> {
        shouldReturnError?.let { return Result.Error(it) }
        return ordersFlow.value.firstOrNull { it.id == orderId }
            ?.let { Result.Success(it) }
            ?: Result.Error(DataError.Network.NOT_FOUND)
    }

    override suspend fun createOrder(
        userId: String,
        order: Order
    ): EmptyResult<DataError.Network> {
        shouldReturnError?.let { return Result.Error(it) }
        lastCreatedOrder = order
        return Result.Success(Unit)
    }

    override suspend fun updateOrder(
        userId: String,
        order: Order
    ): EmptyResult<DataError.Network> {
        shouldReturnError?.let { return Result.Error(it) }
        lastUpdatedOrder = order
        return Result.Success(Unit)
    }

    override suspend fun updateOrderStatus(
        userId: String,
        orderId: String,
        newStatus: OrderStatus
    ): EmptyResult<DataError.Network> {
        shouldReturnError?.let { return Result.Error(it) }
        lastStatusUpdate = orderId to newStatus
        return Result.Success(Unit)
    }

    override suspend fun deleteOrder(
        userId: String,
        orderId: String
    ): EmptyResult<DataError.Network> {
        shouldReturnError?.let { return Result.Error(it) }
        lastDeletedOrderId = orderId
        return Result.Success(Unit)
    }

    override fun newOrderId(userId: String): String = "fake-order-${nextIdSuffix++}"

    override suspend fun uploadFabricPhoto(
        userId: String,
        orderId: String,
        itemId: String,
        photoBytes: ByteArray
    ): Result<Pair<String, String>, DataError.Network> {
        shouldReturnError?.let { return Result.Error(it) }
        return Result.Success(
            "https://fake.storage/$orderId/$itemId.jpg" to "orders/$orderId/items/$itemId.jpg"
        )
    }
}
