package com.danzucker.stitchpad.feature.order.data

import com.danzucker.stitchpad.core.domain.model.FabricImageRef
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import com.danzucker.stitchpad.core.data.dto.PaymentDto
import com.danzucker.stitchpad.core.data.dto.StatusChangeDto
import com.danzucker.stitchpad.core.domain.model.StatusChange
import com.danzucker.stitchpad.core.domain.model.StyleImageRef
import com.danzucker.stitchpad.core.domain.model.StyleImageSource
import com.danzucker.stitchpad.core.domain.model.ownedStoragePaths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrderOfflineWriteRegressionTest {

    @Test
    fun paymentDtosForOfflineAppend_includesLegacyDepositAndNewPaymentOnly() {
        val legacy = payment(id = "legacy-deposit", amount = 10_000.0)
        val oldNormal = payment(id = "old-payment", amount = 2_000.0)
        val newPayment = payment(id = "new-payment", amount = 5_000.0)

        val dtos = paymentDtosForOfflineAppend(
            payment = newPayment,
            knownPayments = listOf(legacy, oldNormal),
        )

        assertEquals(listOf("legacy-deposit", "new-payment"), dtos.map { it.id })
        assertEquals(listOf(10_000.0, 5_000.0), dtos.map { it.amount })
    }

    // Regression: gitlive's FieldValue.arrayUnion does not serialize @Serializable
    // DTOs — passing one crashes iOS with "Unsupported type: com.danzucker…".
    // arrayUnion elements must be primitive maps whose keys mirror the DTO's
    // serialized field names so OrderDto snapshot reads still round-trip.

    @Test
    fun statusChangeDto_toFirestoreMap_matchesDtoFieldsWithPrimitiveValues() {
        val map = StatusChangeDto(status = "IN_PROGRESS", changedAt = 1_700_000_000_000L)
            .toFirestoreMap()

        assertEquals(
            mapOf<String, Any?>(
                "status" to "IN_PROGRESS",
                "changedAt" to 1_700_000_000_000L,
            ),
            map,
        )
        assertAllValuesAreFirestorePrimitives(map)
    }

    @Test
    fun paymentDto_toFirestoreMap_matchesDtoFieldsWithPrimitiveValues() {
        val map = PaymentDto(
            id = "pay-1",
            amount = 5_000.0,
            method = "CASH",
            type = "DEPOSIT",
            recordedAt = 1_700_000_000_000L,
            note = null,
        ).toFirestoreMap()

        assertEquals(
            mapOf<String, Any?>(
                "id" to "pay-1",
                "amount" to 5_000.0,
                "method" to "CASH",
                "type" to "DEPOSIT",
                "recordedAt" to 1_700_000_000_000L,
                "note" to null,
            ),
            map,
        )
        assertAllValuesAreFirestorePrimitives(map)
    }

    private fun assertAllValuesAreFirestorePrimitives(map: Map<String, Any?>) {
        map.forEach { (key, value) ->
            assertTrue(
                value == null || value is String || value is Long || value is Double || value is Boolean,
                "Field '$key' is ${value?.let { it::class.simpleName }} — gitlive arrayUnion " +
                    "rejects non-primitive types on iOS",
            )
        }
    }

    @Test
    fun ownedOrderStoragePaths_collectsLegacyAndMultiImagePathsWithoutDuplicates() {
        val order = order(
            item = OrderItem(
                id = "item-1",
                garmentType = GarmentType.SHIRT,
                description = "Shirt",
                price = 1_000.0,
                stylePhotoStoragePath = "users/u/orders/o/styles/legacy.jpg",
                fabricPhotoStoragePath = "users/u/orders/o/fabrics/legacy.jpg",
                styleImages = listOf(
                    StyleImageRef(
                        source = StyleImageSource.UPLOADED,
                        photoStoragePath = "users/u/orders/o/styles/new.jpg",
                    ),
                    StyleImageRef(
                        source = StyleImageSource.LIBRARY,
                        styleId = "style-1",
                    ),
                ),
                fabricImages = listOf(
                    FabricImageRef(
                        photoUrl = "",
                        photoStoragePath = "users/u/orders/o/fabrics/new.jpg",
                    ),
                    FabricImageRef(
                        photoUrl = "",
                        photoStoragePath = "users/u/orders/o/fabrics/legacy.jpg",
                    ),
                ),
            )
        )

        assertEquals(
            listOf(
                "users/u/orders/o/fabrics/legacy.jpg",
                "users/u/orders/o/styles/legacy.jpg",
                "users/u/orders/o/fabrics/new.jpg",
                "users/u/orders/o/styles/new.jpg",
            ),
            order.ownedStoragePaths(),
        )
    }

    private fun payment(
        id: String,
        amount: Double,
    ): Payment = Payment(
        id = id,
        amount = amount,
        method = PaymentMethod.CASH,
        type = PaymentType.DEPOSIT,
        recordedAt = 1_700_000_000_000L,
        note = null,
    )

    private fun order(item: OrderItem): Order = Order(
        id = "order-1",
        userId = "user-1",
        customerId = "customer-1",
        customerName = "Ada",
        items = listOf(item),
        status = OrderStatus.PENDING,
        priority = OrderPriority.NORMAL,
        statusHistory = listOf(StatusChange(OrderStatus.PENDING, 1_700_000_000_000L)),
        totalPrice = 1_000.0,
        payments = emptyList(),
        deadline = null,
        notes = null,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
    )
}
