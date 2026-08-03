package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.OrderBaseDto
import com.danzucker.stitchpad.core.data.dto.OrderItemBaseDto
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderCost
import com.danzucker.stitchpad.core.domain.model.CostCategory
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import com.danzucker.stitchpad.core.domain.model.StyleImageRef
import com.danzucker.stitchpad.core.domain.model.StyleImageSource
import kotlinx.serialization.descriptors.elementNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Slice 8d-1 (stop-dual-write): the base order doc must stop carrying money. The
 * base-write DTO's WIRE SHAPE therefore must not declare any money field — GitLive
 * encodes every declared field (incl. defaults), so a field that stays on the DTO
 * keeps being written to base and would re-appear after the Slice-8d strip, breaking
 * the staff field-absence read gate. Money lives only in `/private/money` now.
 */
class OrderBaseMapperTest {

    private val moneyFields = setOf(
        "totalPrice", "discount", "discountReason",
        "depositPaid", "balanceRemaining", "payments", "costs",
    )

    private fun item(id: String, price: Double) = OrderItem(
        id = id,
        garmentType = GarmentType.SHIRT,
        description = "kaftan",
        price = price,
        quantity = 2,
        fabricName = "ankara",
        styleImages = listOf(
            StyleImageRef(source = StyleImageSource.LIBRARY, styleId = "style-1"),
        ),
    )

    private fun order() = Order(
        id = "order-1",
        userId = "u1",
        customerId = "c1",
        customerName = "Ada",
        items = listOf(item("i1", 1_000.0), item("i2", 2_500.0)),
        status = OrderStatus.IN_PROGRESS,
        priority = OrderPriority.URGENT,
        statusHistory = emptyList(),
        totalPrice = 40_000.0,
        discount = 5_000.0,
        discountReason = "loyal",
        payments = listOf(Payment("p1", 10_000.0, PaymentMethod.OTHER, PaymentType.DEPOSIT, 5L, null)),
        costs = listOf(OrderCost(CostCategory.FABRIC, 3_000.0, null)),
        deadline = 123L,
        notes = "rush",
        createdAt = 10L,
        updatedAt = 10L,
    )

    @Test
    fun baseDto_wireShape_declaresNoMoneyField() {
        val names = OrderBaseDto.serializer().descriptor.elementNames.toSet()

        moneyFields.forEach { field ->
            assertFalse(field in names, "OrderBaseDto must not declare money field '$field'")
        }
    }

    @Test
    fun baseItemDto_wireShape_declaresNoPrice() {
        val names = OrderItemBaseDto.serializer().descriptor.elementNames.toSet()

        assertFalse("price" in names, "OrderItemBaseDto must not declare 'price' (relocated to /private itemPrices)")
    }

    @Test
    fun baseDto_wireShape_keepsNonMoneyWorkFields() {
        val names = OrderBaseDto.serializer().descriptor.elementNames.toSet()

        listOf("id", "customerId", "customerName", "status", "subStatus", "priority",
            "deadline", "notes", "archivedAt", "items", "statusHistory", "createdAt", "updatedAt")
            .forEach { field -> assertTrue(field in names, "OrderBaseDto must keep work field '$field'") }
    }

    @Test
    fun toOrderBaseDto_carriesEveryNonMoneyField() {
        val dto = order().toOrderBaseDto()

        assertEquals("order-1", dto.id)
        assertEquals("c1", dto.customerId)
        assertEquals("Ada", dto.customerName)
        assertEquals(OrderStatus.IN_PROGRESS.name, dto.status)
        assertEquals(OrderPriority.URGENT.name, dto.priority)
        assertEquals(123L, dto.deadline)
        assertEquals("rush", dto.notes)
        assertEquals(2, dto.items.size)
    }

    @Test
    fun toOrderBaseDto_item_carriesWorkFieldsButNotPrice() {
        val dto = order().toOrderBaseDto()

        val first = dto.items.first { it.id == "i1" }
        assertEquals(GarmentType.SHIRT.name, first.garmentType)
        assertEquals("kaftan", first.description)
        assertEquals(2, first.quantity)
        assertEquals("ankara", first.fabricName)
        // Style image carried over (work data staff need).
        assertEquals("style-1", first.styleId)
    }
}
