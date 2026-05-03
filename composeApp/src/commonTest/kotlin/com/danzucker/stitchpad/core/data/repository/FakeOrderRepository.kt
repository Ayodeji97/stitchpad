package com.danzucker.stitchpad.core.data.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus
import com.danzucker.stitchpad.core.domain.model.Payment
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
    var lastRecordedPayment: Pair<String, Payment>? = null
    var lastSubStatusUpdate: Pair<String, OrderSubStatus?>? = null
    var lastNotesUpdate: Pair<String, String?>? = null
    var lastArchivedOrderId: String? = null
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
        ordersFlow.value = ordersFlow.value + order
        return Result.Success(Unit)
    }

    override suspend fun updateOrder(
        userId: String,
        order: Order
    ): EmptyResult<DataError.Network> {
        shouldReturnError?.let { return Result.Error(it) }
        lastUpdatedOrder = order
        ordersFlow.value = ordersFlow.value.map { if (it.id == order.id) order else it }
        return Result.Success(Unit)
    }

    override suspend fun updateOrderStatus(
        userId: String,
        orderId: String,
        newStatus: OrderStatus
    ): EmptyResult<DataError.Network> {
        shouldReturnError?.let { return Result.Error(it) }
        lastStatusUpdate = orderId to newStatus
        ordersFlow.value = ordersFlow.value.map { existing ->
            if (existing.id == orderId) existing.copy(status = newStatus) else existing
        }
        return Result.Success(Unit)
    }

    override suspend fun deleteOrder(
        userId: String,
        orderId: String
    ): EmptyResult<DataError.Network> {
        shouldReturnError?.let { return Result.Error(it) }
        lastDeletedOrderId = orderId
        ordersFlow.value = ordersFlow.value.filterNot { it.id == orderId }
        return Result.Success(Unit)
    }

    override suspend fun recordPayment(
        userId: String,
        orderId: String,
        payment: Payment,
    ): EmptyResult<DataError.Network> {
        shouldReturnError?.let { return Result.Error(it) }
        lastRecordedPayment = orderId to payment
        ordersFlow.value = ordersFlow.value.map { existing ->
            if (existing.id == orderId) existing.copy(payments = existing.payments + payment) else existing
        }
        return Result.Success(Unit)
    }

    override suspend fun updateSubStatus(
        userId: String,
        orderId: String,
        subStatus: OrderSubStatus?,
    ): EmptyResult<DataError.Network> {
        shouldReturnError?.let { return Result.Error(it) }
        lastSubStatusUpdate = orderId to subStatus
        ordersFlow.value = ordersFlow.value.map { existing ->
            if (existing.id == orderId) existing.copy(subStatus = subStatus) else existing
        }
        return Result.Success(Unit)
    }

    override suspend fun updateNotes(
        userId: String,
        orderId: String,
        notes: String?,
    ): EmptyResult<DataError.Network> {
        shouldReturnError?.let { return Result.Error(it) }
        lastNotesUpdate = orderId to notes
        ordersFlow.value = ordersFlow.value.map { existing ->
            if (existing.id == orderId) existing.copy(notes = notes) else existing
        }
        return Result.Success(Unit)
    }

    override suspend fun archiveOrder(
        userId: String,
        orderId: String,
    ): EmptyResult<DataError.Network> {
        shouldReturnError?.let { return Result.Error(it) }
        lastArchivedOrderId = orderId
        ordersFlow.value = ordersFlow.value.map { existing ->
            if (existing.id == orderId) existing.copy(archivedAt = 1L) else existing
        }
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
