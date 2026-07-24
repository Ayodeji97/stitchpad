package com.danzucker.stitchpad.core.sharing

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
import com.danzucker.stitchpad.core.domain.model.User
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression guard for [ReceiptFormatter]/[ReceiptData]: costs and profit are
 * private (tailor-only) figures and must never surface on a customer-facing
 * receipt. Today this holds by construction because [ReceiptData] is an
 * explicit field allow-list that never reads [Order.costs] or [Order.profit]
 * — this test exists so a future field addition can't silently leak them.
 */
class ReceiptCostPrivacyTest {

    private fun depositPayment(amount: Double, recordedAt: Long = 0L): Payment = Payment(
        id = "test-deposit",
        amount = amount,
        method = PaymentMethod.OTHER,
        type = PaymentType.DEPOSIT,
        recordedAt = recordedAt,
    )

    private val testUser = User(
        id = "user1",
        email = "ade@test.com",
        displayName = "Ade",
        businessName = "Ade's Tailoring",
        phoneNumber = null,
        whatsappNumber = "08012345678",
        avatarColorIndex = 0
    )

    private val testOrder = Order(
        id = "abc123def456",
        userId = "user1",
        customerId = "cust1",
        customerName = "Chief Okafor",
        items = listOf(
            OrderItem(
                id = "item1",
                garmentType = GarmentType.AGBADA,
                description = "White Agbada",
                price = 45000.0
            ),
            OrderItem(
                id = "item2",
                garmentType = GarmentType.SENATOR,
                description = "Black Senator",
                price = 25000.0
            )
        ),
        status = OrderStatus.IN_PROGRESS,
        priority = OrderPriority.RUSH,
        statusHistory = emptyList(),
        totalPrice = 70000.0,
        payments = listOf(depositPayment(30000.0)),
        deadline = 1745798400000L,
        notes = null,
        createdAt = 1745193600000L,
        updatedAt = 1745193600000L
    )

    private val garmentNames = mapOf(
        GarmentType.AGBADA to "Agbada",
        GarmentType.SENATOR to "Senator"
    )

    @Test
    fun `receipt never contains cost or profit figures`() {
        // totalPrice 50,000 with a 44,000 fabric cost -> totalCost 44,000, profit 6,000.
        // Both figures are private and must never reach ReceiptData.
        val order = testOrder.copy(
            totalPrice = 50_000.0,
            payments = emptyList(),
            costs = listOf(OrderCost(CostCategory.FABRIC, 44_000.0)),
        )
        require(order.totalCost == 44_000.0)
        require(order.profit == 6_000.0)

        val receipt = ReceiptFormatter.format(order, testUser, garmentNames)

        val haystack = buildList {
            add(receipt.businessName)
            receipt.businessPhone?.let(::add)
            add(receipt.documentTypeLabel)
            add(receipt.customerName)
            add(receipt.dateFormatted)
            add(receipt.subtotalFormatted)
            receipt.discountFormatted?.let(::add)
            receipt.discountReason?.let(::add)
            add(receipt.totalFormatted)
            add(receipt.depositFormatted)
            add(receipt.balanceFormatted)
            add(receipt.statusLabel)
            add(receipt.statusColorHex)
            receipt.deadlineFormatted?.let(::add)
            receipt.priorityLabel?.let(::add)
            add(receipt.orderIdShort)
            receipt.items.forEach { item ->
                add(item.garmentName)
                add(item.formattedUnitPrice)
                add(item.formattedPrice)
            }
            receipt.paymentRows.forEach { row ->
                add(row.dateFormatted)
                add(row.typeLabel)
                add(row.methodLabel)
                add(row.formattedAmount)
            }
            receipt.bankBlock?.let { bank ->
                add(bank.bankName)
                add(bank.accountName)
                add(bank.accountNumber)
            }
        }

        // "44,000" is the cost figure, "6,000" is the profit figure. Neither
        // must appear anywhere on the customer-facing receipt.
        assertFalse(haystack.any { it.contains("44,000") })
        assertFalse(haystack.any { it.contains("6,000") })
        assertTrue(haystack.isNotEmpty())
    }
}
