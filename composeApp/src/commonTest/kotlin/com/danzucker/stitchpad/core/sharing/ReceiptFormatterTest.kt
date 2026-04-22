package com.danzucker.stitchpad.core.sharing

import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReceiptFormatterTest {

    private val testUser = User(
        id = "user1",
        email = "ade@test.com",
        displayName = "Ade",
        businessName = "Ade's Tailoring",
        phoneNumber = "08012345678",
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
        depositPaid = 30000.0,
        balanceRemaining = 40000.0,
        deadline = 1745798400000L,
        notes = null,
        createdAt = 1745193600000L,
        updatedAt = 1745193600000L
    )

    private val garmentNames = mapOf(
        GarmentType.AGBADA to "Agbada",
        GarmentType.SENATOR to "Senator"
    )

    private fun formatResult() = ReceiptFormatter.format(testOrder, testUser, garmentNames)

    @Test
    fun formatsBusinessNameWithScissorsEmoji() {
        val result = formatResult()
        assertEquals("\u2702\uFE0F Ade's Tailoring", result.businessName)
    }

    @Test
    fun formatsPhoneWithEmoji() {
        val result = formatResult()
        assertEquals("\uD83D\uDCDE 08012345678", result.businessPhone)
    }

    @Test
    fun nullBusinessNameFallsBackToStitchPad() {
        val userNoName = testUser.copy(businessName = null)
        val result = ReceiptFormatter.format(testOrder, userNoName, garmentNames)
        assertEquals("\u2702\uFE0F StitchPad", result.businessName)
    }

    @Test
    fun nullPhoneReturnsNull() {
        val userNoPhone = testUser.copy(phoneNumber = null)
        val result = ReceiptFormatter.format(testOrder, userNoPhone, garmentNames)
        assertNull(result.businessPhone)
    }

    @Test
    fun customerNamePassedThrough() {
        val result = formatResult()
        assertEquals("Chief Okafor", result.customerName)
    }

    @Test
    fun itemsGroupedByGarmentType() {
        val orderWithDuplicates = testOrder.copy(
            items = listOf(
                OrderItem("i1", GarmentType.TROUSER, "T1", 5000.0),
                OrderItem("i2", GarmentType.TROUSER, "T2", 7000.0)
            )
        )
        val names = mapOf(GarmentType.TROUSER to "Trouser")
        val result = ReceiptFormatter.format(orderWithDuplicates, testUser, names)
        assertEquals(1, result.items.size)
        assertEquals(2, result.items[0].quantity)
        assertEquals("\u20A612,000", result.items[0].formattedPrice)
    }

    @Test
    fun paymentFormattedWithNaira() {
        val result = formatResult()
        assertEquals("\u20A670,000", result.totalFormatted)
        assertEquals("\u20A630,000", result.depositFormatted)
        assertEquals("\u20A640,000", result.balanceFormatted)
    }

    @Test
    fun balanceRemainingMeansNotFullyPaid() {
        val result = formatResult()
        assertFalse(result.isFullyPaid)
    }

    @Test
    fun zeroBalanceMeansFullyPaid() {
        val paidOrder = testOrder.copy(balanceRemaining = 0.0)
        val result = ReceiptFormatter.format(paidOrder, testUser, garmentNames)
        assertTrue(result.isFullyPaid)
    }

    @Test
    fun rushPriorityShowsLabel() {
        val result = formatResult()
        assertEquals("RUSH", result.priorityLabel)
    }

    @Test
    fun urgentPriorityShowsLabel() {
        val urgentOrder = testOrder.copy(priority = OrderPriority.URGENT)
        val result = ReceiptFormatter.format(urgentOrder, testUser, garmentNames)
        assertEquals("URGENT", result.priorityLabel)
    }

    @Test
    fun normalPriorityReturnsNull() {
        val normalOrder = testOrder.copy(priority = OrderPriority.NORMAL)
        val result = ReceiptFormatter.format(normalOrder, testUser, garmentNames)
        assertNull(result.priorityLabel)
    }

    @Test
    fun deadlineFormattedCorrectly() {
        val result = formatResult()
        val deadline = requireNotNull(result.deadlineFormatted)
        assertFalse(deadline.isBlank())
        assertTrue(deadline.contains("2025"))
    }

    @Test
    fun nullDeadlineReturnsNull() {
        val noDeadline = testOrder.copy(deadline = null)
        val result = ReceiptFormatter.format(noDeadline, testUser, garmentNames)
        assertNull(result.deadlineFormatted)
    }

    @Test
    fun orderIdTruncatedToShortForm() {
        val result = formatResult()
        assertEquals("ORD-ABC1", result.orderIdShort)
    }

    @Test
    fun statusLabelMapped() {
        val result = formatResult()
        assertEquals("In Progress", result.statusLabel)
    }

    @Test
    fun unpaidOrderUsesInvoiceLabel() {
        val result = formatResult()
        assertEquals("INVOICE", result.documentTypeLabel)
    }

    @Test
    fun fullyPaidOrderUsesReceiptLabel() {
        val paidOrder = testOrder.copy(balanceRemaining = 0.0)
        val result = ReceiptFormatter.format(paidOrder, testUser, garmentNames)
        assertEquals("RECEIPT", result.documentTypeLabel)
    }

    @Test
    fun businessNameSetShowsAttribution() {
        val result = formatResult()
        assertEquals("Generated by StitchPad", result.attribution)
    }

    @Test
    fun nullBusinessNameSuppressesAttribution() {
        val userNoName = testUser.copy(businessName = null)
        val result = ReceiptFormatter.format(testOrder, userNoName, garmentNames)
        assertNull(result.attribution)
    }
}
