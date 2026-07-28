package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.domain.model.CostCategory
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderCost
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import kotlin.test.Test
import kotlin.test.assertEquals

class OrderMoneyMapperTest {

    private fun order(
        items: List<OrderItem>,
        totalPrice: Double = 0.0,
        discount: Double = 0.0,
        discountReason: String? = null,
        payments: List<Payment> = emptyList(),
        costs: List<OrderCost> = emptyList(),
    ) = Order(
        id = "order-1",
        userId = "u1",
        customerId = "c1",
        customerName = "Ada",
        items = items,
        status = OrderStatus.PENDING,
        priority = OrderPriority.NORMAL,
        statusHistory = emptyList(),
        totalPrice = totalPrice,
        discount = discount,
        discountReason = discountReason,
        payments = payments,
        costs = costs,
        deadline = null,
        notes = null,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun item(id: String, price: Double) = OrderItem(
        id = id,
        garmentType = GarmentType.SHIRT,
        description = "d",
        price = price,
    )

    @Test
    fun maps_scalar_money_and_discount_reason() {
        val dto = order(
            items = listOf(item("i1", 1_000.0)),
            totalPrice = 40_000.0,
            discount = 5_000.0,
            discountReason = "loyal",
        ).toOrderMoneyDto()

        assertEquals(40_000.0, dto.totalPrice)
        assertEquals(5_000.0, dto.discount)
        assertEquals("loyal", dto.discountReason)
    }

    @Test
    fun relocates_each_item_price_keyed_by_item_id() {
        val dto = order(items = listOf(item("i1", 1_000.0), item("i2", 2_500.0))).toOrderMoneyDto()

        assertEquals(mapOf("i1" to 1_000.0, "i2" to 2_500.0), dto.itemPrices)
    }

    @Test
    fun maps_payments_and_costs() {
        val payment = Payment("p1", 10_000.0, PaymentMethod.OTHER, PaymentType.DEPOSIT, 5L, "note")
        val cost = OrderCost(CostCategory.FABRIC, 3_000.0, "ankara")

        val dto = order(
            items = listOf(item("i1", 1_000.0)),
            payments = listOf(payment),
            costs = listOf(cost),
        ).toOrderMoneyDto()

        assertEquals(1, dto.payments.size)
        assertEquals(10_000.0, dto.payments.first().amount)
        assertEquals(PaymentType.DEPOSIT.name, dto.payments.first().type)
        assertEquals(1, dto.costs.size)
        assertEquals(3_000.0, dto.costs.first().amount)
        assertEquals(CostCategory.FABRIC.name, dto.costs.first().category)
    }
}
