package com.danzucker.stitchpad.feature.order.data

import com.danzucker.stitchpad.core.domain.model.CostCategory
import com.danzucker.stitchpad.core.domain.model.FabricImageRef
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderCost
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import com.danzucker.stitchpad.core.data.dto.OrderCostDto
import com.danzucker.stitchpad.core.data.dto.OrderMoneyDto
import com.danzucker.stitchpad.core.data.dto.PaymentDto
import com.danzucker.stitchpad.core.data.dto.StatusChangeDto
import com.danzucker.stitchpad.core.domain.model.StatusChange
import com.danzucker.stitchpad.core.domain.model.StyleImageRef
import com.danzucker.stitchpad.core.domain.model.StyleImageSource
import com.danzucker.stitchpad.core.domain.model.ownedStoragePaths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    // Regression: updateCosts must never bump updatedAt. Costs are private analytics data —
    // backfilling costs onto an old order must not re-bucket it into the current Reports
    // window (KpiCalculator buckets by updatedAt). See FirebaseOrderRepository.updateCosts KDoc.
    @Test
    fun orderCostsWriteFields_containsCostsOnly_neverUpdatedAt() {
        val costs = listOf(
            OrderCost(category = CostCategory.FABRIC, amount = 3_000.0, note = null),
            OrderCost(category = CostCategory.LABOUR, amount = 2_000.0, note = "tailoring"),
        )

        val fields = orderCostsWriteFields(costs)

        assertTrue(fields.containsKey("costs"))
        assertFalse(fields.containsKey("updatedAt"))
        assertEquals(
            listOf(
                mapOf<String, Any?>("category" to "FABRIC", "amount" to 3_000.0, "note" to null),
                mapOf<String, Any?>("category" to "LABOUR", "amount" to 2_000.0, "note" to "tailoring"),
            ),
            fields["costs"],
        )
    }

    // Slice 8d-1 (stop-dual-write): recordPayment must stop mirroring money onto the
    // base order doc. The base write may only touch non-sensitive fields (updatedAt);
    // `payments` and the legacy `depositPaid` now live solely in /private/money.
    @Test
    fun orderPaymentBaseWriteFields_bumpsUpdatedAtOnly_neverMoney() {
        val fields = orderPaymentBaseWriteFields(now = 1_700_000_000_000L)

        assertEquals(mapOf<String, Any?>("updatedAt" to 1_700_000_000_000L), fields)
        assertFalse(fields.containsKey("payments"))
        assertFalse(fields.containsKey("depositPaid"))
    }

    // Regression (Cursor Bugbot, PR #350): updateOrder used to REPLACE the /private/money
    // mirror. A legacy order with no mirror + recordPayment produces an UNSTAMPED partial
    // mirror that the read path (Order.withMoney) ignores — so the in-memory Order never
    // carries that payment, and the replace deleted it for good. The merge payload must
    // therefore carry every money field EXCEPT payments, which are unioned separately.
    @Test
    fun orderMoneyMergeFields_containsEveryMoneyFieldExceptPayments() {
        val fields = orderMoneyMergeFields(money())

        assertEquals(
            setOf("ownerId", "orderId", "totalPrice", "discount", "discountReason", "costs", "itemPrices"),
            fields.keys,
        )
        assertEquals("user-1", fields["ownerId"])
        assertEquals("order-1", fields["orderId"])
        assertEquals(12_000.0, fields["totalPrice"])
        assertEquals(500.0, fields["discount"])
        assertEquals("loyal customer", fields["discountReason"])
        // Costs stay replace-whole (clearing costs is a legitimate state) and must be
        // primitive maps — gitlive rejects @Serializable DTOs inside raw map writes on iOS.
        assertEquals(
            listOf(mapOf<String, Any?>("category" to "FABRIC", "amount" to 3_000.0, "note" to null)),
            fields["costs"],
        )
        assertEquals(mapOf("item-1" to 12_000.0), fields["itemPrices"])
        assertAllValuesAreFirestorePrimitives(
            fields.filterKeys { it !in setOf("costs", "itemPrices") },
        )
    }

    @Test
    fun orderMoneyMergeFields_neverContainsPayments() {
        val fields = orderMoneyMergeFields(money(payments = listOf(paymentDto("pay-1"))))

        assertFalse(fields.containsKey("payments"))
    }

    // Empty payments must OMIT the key entirely: writing an empty union (or an empty list)
    // would be pointless at best and clobbering at worst — merge has to leave whatever the
    // unstamped mirror already holds untouched.
    @Test
    fun orderMoneyMergeWrite_omitsPaymentsKeyWhenOrderHasNoPayments() {
        val fields = orderMoneyMergeWrite(money(payments = emptyList())) { error("must not be called") }

        assertFalse(fields.containsKey("payments"))
        assertEquals(orderMoneyMergeFields(money(payments = emptyList())), fields)
    }

    @Test
    fun orderMoneyMergeWrite_unionsPaymentsAsPrimitiveMapsWhenPresent() {
        var unioned: List<Map<String, Any?>>? = null

        val fields = orderMoneyMergeWrite(
            money(payments = listOf(paymentDto("pay-1"), paymentDto("pay-2"))),
        ) { payments ->
            unioned = payments
            "array-union"
        }

        assertEquals("array-union", fields["payments"])
        assertEquals(listOf("pay-1", "pay-2"), unioned?.map { it["id"] })
        unioned?.forEach { assertAllValuesAreFirestorePrimitives(it) }
    }

    private fun money(
        payments: List<PaymentDto> = emptyList(),
    ): OrderMoneyDto = OrderMoneyDto(
        ownerId = "user-1",
        orderId = "order-1",
        totalPrice = 12_000.0,
        discount = 500.0,
        discountReason = "loyal customer",
        payments = payments,
        costs = listOf(OrderCostDto(category = "FABRIC", amount = 3_000.0, note = null)),
        itemPrices = mapOf("item-1" to 12_000.0),
    )

    private fun paymentDto(id: String): PaymentDto = PaymentDto(
        id = id,
        amount = 5_000.0,
        method = "CASH",
        type = "DEPOSIT",
        recordedAt = 1_700_000_000_000L,
        note = null,
    )

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
    fun orderAssignmentWriteFields_touchesOnlyAssignmentAndUpdatedAt() {
        val fields = orderAssignmentWriteFields(memberId = "m1", memberName = "Chidi O", now = 42L)
        assertEquals(setOf("assignedMemberId", "assignedMemberName", "updatedAt"), fields.keys)
        assertEquals("m1", fields["assignedMemberId"])
    }

    @Test
    fun orderAssignmentWriteFields_unassignWritesExplicitNulls() {
        val fields = orderAssignmentWriteFields(memberId = null, memberName = null, now = 42L)
        assertEquals(setOf("assignedMemberId", "assignedMemberName", "updatedAt"), fields.keys)
        assertEquals(null, fields["assignedMemberId"])
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
