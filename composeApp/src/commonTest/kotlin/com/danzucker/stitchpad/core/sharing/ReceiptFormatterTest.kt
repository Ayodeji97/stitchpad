package com.danzucker.stitchpad.core.sharing

import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.domain.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReceiptFormatterTest {

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

    private fun formatResult() = ReceiptFormatter.format(testOrder, testUser, garmentNames)

    @Test
    fun formatsBusinessName() {
        val result = formatResult()
        assertEquals("Ade's Tailoring", result.businessName)
    }

    @Test
    fun formatsPhone() {
        val result = formatResult()
        assertEquals("08012345678", result.businessPhone)
    }

    @Test
    fun nullBusinessNameFallsBackToStitchPad() {
        val userNoName = testUser.copy(businessName = null)
        val result = ReceiptFormatter.format(testOrder, userNoName, garmentNames)
        assertEquals("StitchPad", result.businessName)
    }

    @Test
    fun nullPhoneReturnsNull() {
        val userNoPhone = testUser.copy(whatsappNumber = null)
        val result = ReceiptFormatter.format(testOrder, userNoPhone, garmentNames)
        assertNull(result.businessPhone)
    }

    @Test
    fun customerNamePassedThrough() {
        val result = formatResult()
        assertEquals("Chief Okafor", result.customerName)
    }

    @Test
    fun itemsGroupedByGarmentTypeAndUnitPrice() {
        // Same garment type AND same unit price collapse into one line so the
        // line can carry an honest unit price (qty summed, total = unit \u00D7 qty).
        val orderWithDuplicates = testOrder.copy(
            items = listOf(
                OrderItem(id = "i1", garmentType = GarmentType.TROUSER, description = "T1", price = 5000.0, quantity = 2),
                OrderItem(id = "i2", garmentType = GarmentType.TROUSER, description = "T2", price = 5000.0, quantity = 3)
            )
        )
        val names = mapOf(GarmentType.TROUSER to "Trouser")
        val result = ReceiptFormatter.format(orderWithDuplicates, testUser, names)
        assertEquals(1, result.items.size)
        assertEquals(5, result.items[0].quantity)
        assertEquals("\u20A65,000", result.items[0].formattedUnitPrice)
        assertEquals("\u20A625,000", result.items[0].formattedPrice)
    }

    @Test
    fun sameGarmentDifferentUnitPriceSplitsIntoSeparateLines() {
        // Differently-priced items of the same garment type must NOT merge \u2014
        // a blended line could not show a truthful unit price.
        val orderWithMixedPrices = testOrder.copy(
            items = listOf(
                OrderItem(id = "i1", garmentType = GarmentType.TROUSER, description = "T1", price = 5000.0, quantity = 2),
                OrderItem(id = "i2", garmentType = GarmentType.TROUSER, description = "T2", price = 7000.0, quantity = 3)
            )
        )
        val names = mapOf(GarmentType.TROUSER to "Trouser")
        val result = ReceiptFormatter.format(orderWithMixedPrices, testUser, names)
        assertEquals(2, result.items.size)
        val byUnit = result.items.associateBy { it.formattedUnitPrice }
        assertEquals(2, byUnit.getValue("\u20A65,000").quantity)
        assertEquals("\u20A610,000", byUnit.getValue("\u20A65,000").formattedPrice)
        assertEquals(3, byUnit.getValue("\u20A67,000").quantity)
        assertEquals("\u20A621,000", byUnit.getValue("\u20A67,000").formattedPrice)
    }

    @Test
    fun singleItemExposesUnitPrice() {
        val result = formatResult()
        // testOrder's first line: one Agbada at \u20A645,000.
        val agbada = result.items.first { it.garmentName == "Agbada" }
        assertEquals(1, agbada.quantity)
        assertEquals("\u20A645,000", agbada.formattedUnitPrice)
        assertEquals("\u20A645,000", agbada.formattedPrice)
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
        val paidOrder = testOrder.copy(payments = listOf(depositPayment(amount = testOrder.totalPrice)))
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
    fun orderWithNoPaymentsUsesInvoiceLabel() {
        val unpaid = testOrder.copy(payments = emptyList())
        val result = ReceiptFormatter.format(unpaid, testUser, garmentNames)
        assertEquals("INVOICE", result.documentTypeLabel)
        assertEquals(ReceiptDocumentType.INVOICE, result.documentType)
    }

    @Test
    fun partialPaidOrderUsesDepositReceiptLabel() {
        // testOrder has a 30k deposit on a 70k order — partial paid.
        val result = formatResult()
        assertEquals("DEPOSIT RECEIPT", result.documentTypeLabel)
        assertEquals(ReceiptDocumentType.DEPOSIT_RECEIPT, result.documentType)
    }

    @Test
    fun fullyPaidOrderUsesReceiptLabel() {
        val paidOrder = testOrder.copy(payments = listOf(depositPayment(amount = testOrder.totalPrice)))
        val result = ReceiptFormatter.format(paidOrder, testUser, garmentNames)
        assertEquals("RECEIPT", result.documentTypeLabel)
        assertEquals(ReceiptDocumentType.RECEIPT, result.documentType)
    }

    @Test
    fun defaultTierFreeShowsFullAttribution() {
        // Default formatter call (no explicit tier) uses FREE → full footer.
        val result = formatResult()
        assertEquals(ReceiptAttribution.Full, result.attribution)
    }

    @Test
    fun defaultTierFreeUsesStitchPadDiagonalWatermark() {
        val result = formatResult()
        assertEquals(WatermarkSpec.StitchPadDiagonal, result.watermark)
    }

    // --- Payment rows ---

    @Test
    fun paymentRowsAreEmptyWhenNoPayments() {
        val unpaid = testOrder.copy(payments = emptyList())
        val result = ReceiptFormatter.format(unpaid, testUser, garmentNames)
        assertTrue(result.paymentRows.isEmpty())
    }

    @Test
    fun paymentRowsSortedByRecordedAtAscending() {
        val later = Payment(
            id = "p2",
            amount = 10_000.0,
            method = PaymentMethod.TRANSFER,
            type = PaymentType.PROGRESS,
            recordedAt = 2_000L,
        )
        val earlier = Payment(
            id = "p1",
            amount = 30_000.0,
            method = PaymentMethod.CASH,
            type = PaymentType.DEPOSIT,
            recordedAt = 1_000L,
        )
        val o = testOrder.copy(payments = listOf(later, earlier))
        val result = ReceiptFormatter.format(o, testUser, garmentNames)
        assertEquals(2, result.paymentRows.size)
        // Earlier comes first.
        assertEquals("Deposit", result.paymentRows[0].typeLabel)
        assertEquals("Cash", result.paymentRows[0].methodLabel)
        assertEquals("₦30,000", result.paymentRows[0].formattedAmount)
        assertEquals("Progress", result.paymentRows[1].typeLabel)
        assertEquals("Transfer", result.paymentRows[1].methodLabel)
    }

    // --- Bank block visibility ---

    private val userWithBank = testUser.copy(
        bankName = "GTBank",
        bankAccountName = "Ade Adesola Enterprises",
        bankAccountNumber = "0123456789",
    )

    @Test
    fun bankBlockHiddenWhenUserHasNoBankDetails() {
        val result = formatResult()
        assertNull(result.bankBlock)
    }

    @Test
    fun bankBlockShownWhenAllThreeBankFieldsSet() {
        val result = ReceiptFormatter.format(testOrder, userWithBank, garmentNames)
        val bank = requireNotNull(result.bankBlock)
        assertEquals("GTBank", bank.bankName)
        assertEquals("Ade Adesola Enterprises", bank.accountName)
        assertEquals("0123456789", bank.accountNumber)
    }

    @Test
    fun bankBlockHiddenOnFullyPaidReceiptEvenWhenBankSet() {
        val paid = testOrder.copy(payments = listOf(depositPayment(amount = testOrder.totalPrice)))
        val result = ReceiptFormatter.format(paid, userWithBank, garmentNames)
        assertNull(result.bankBlock)
    }

    @Test
    fun bankBlockRequiresAllThreeFields() {
        val onlyBankName = testUser.copy(bankName = "GTBank")
        assertNull(ReceiptFormatter.format(testOrder, onlyBankName, garmentNames).bankBlock)

        val missingAccountNumber = testUser.copy(
            bankName = "GTBank",
            bankAccountName = "Ade",
            bankAccountNumber = null,
        )
        assertNull(ReceiptFormatter.format(testOrder, missingAccountNumber, garmentNames).bankBlock)
    }

    // --- Document-type override ---

    @Test
    fun forceInvoiceOnPartialPaidOrderRelabelsAsInvoice() {
        val result = ReceiptFormatter.format(
            testOrder, testUser, garmentNames,
            forceDocumentType = ReceiptDocumentType.INVOICE,
        )
        assertEquals(ReceiptDocumentType.INVOICE, result.documentType)
        assertEquals("INVOICE", result.documentTypeLabel)
    }

    @Test
    fun forceInvoiceOnFullyPaidOrderIsIgnored() {
        val paid = testOrder.copy(payments = listOf(depositPayment(amount = testOrder.totalPrice)))
        val result = ReceiptFormatter.format(
            paid, testUser, garmentNames,
            forceDocumentType = ReceiptDocumentType.INVOICE,
        )
        // Override must not relabel a paid document — totals say "balance ₦0".
        assertEquals(ReceiptDocumentType.RECEIPT, result.documentType)
        assertEquals("RECEIPT", result.documentTypeLabel)
    }

    @Test
    fun forceReceiptOnPartialPaidOrderIsIgnored() {
        // Forcing RECEIPT on a partial-paid order would mislabel the document
        // as fully paid while balanceFormatted still reports a positive figure.
        val result = ReceiptFormatter.format(
            testOrder, testUser, garmentNames,
            forceDocumentType = ReceiptDocumentType.RECEIPT,
        )
        assertEquals(ReceiptDocumentType.DEPOSIT_RECEIPT, result.documentType)
        assertEquals("DEPOSIT RECEIPT", result.documentTypeLabel)
    }

    @Test
    fun forceReceiptOnUnpaidOrderIsIgnored() {
        val unpaid = testOrder.copy(payments = emptyList())
        val result = ReceiptFormatter.format(
            unpaid, testUser, garmentNames,
            forceDocumentType = ReceiptDocumentType.RECEIPT,
        )
        assertEquals(ReceiptDocumentType.INVOICE, result.documentType)
        assertEquals("INVOICE", result.documentTypeLabel)
    }

    @Test
    fun forceDepositReceiptOnUnpaidOrderIsIgnored() {
        // A Deposit Receipt requires at least one payment. The override can't
        // synthesize one — silently fall back to the natural INVOICE.
        val unpaid = testOrder.copy(payments = emptyList())
        val result = ReceiptFormatter.format(
            unpaid, testUser, garmentNames,
            forceDocumentType = ReceiptDocumentType.DEPOSIT_RECEIPT,
        )
        assertEquals(ReceiptDocumentType.INVOICE, result.documentType)
    }

    @Test
    fun fullyPaidFlagDerivesFromBalanceNotFromOverride() {
        // Forcing INVOICE framing on a partial-paid order must NOT mark the
        // receipt as fully paid — the bank block visibility and any
        // downstream renderer logic depends on the real outstanding balance.
        val result = ReceiptFormatter.format(
            testOrder, testUser, garmentNames,
            forceDocumentType = ReceiptDocumentType.INVOICE,
        )
        assertFalse(result.isFullyPaid)
    }

    @Test
    fun bankBlockShownWhenInvoiceOverrideAppliedToPartialPaid() {
        // The override re-frames the label; the bank block still needs to show
        // because there is still balance to collect.
        val result = ReceiptFormatter.format(
            testOrder, userWithBank, garmentNames,
            forceDocumentType = ReceiptDocumentType.INVOICE,
        )
        val bank = requireNotNull(result.bankBlock)
        assertEquals("GTBank", bank.bankName)
    }

    // --- effectiveDocumentType (public helper the share-sheet title reads) ---

    @Test
    fun effectiveDocTypeMirrorsFormatOutput() {
        // The sheet title must match what format() will actually produce.
        ReceiptDocumentType.entries.forEach { natural ->
            listOf(null, *ReceiptDocumentType.entries.toTypedArray()).forEach { force ->
                val effective = ReceiptFormatter.effectiveDocumentType(natural, force)
                // No payments -> Invoice only; fully-paid -> Receipt only;
                // partial -> Invoice/Deposit flip allowed.
                when (natural) {
                    ReceiptDocumentType.INVOICE -> assertEquals(ReceiptDocumentType.INVOICE, effective)
                    ReceiptDocumentType.RECEIPT -> assertEquals(ReceiptDocumentType.RECEIPT, effective)
                    ReceiptDocumentType.DEPOSIT_RECEIPT -> {
                        val expected = if (force == ReceiptDocumentType.INVOICE) {
                            ReceiptDocumentType.INVOICE
                        } else {
                            ReceiptDocumentType.DEPOSIT_RECEIPT
                        }
                        assertEquals(expected, effective)
                    }
                }
            }
        }
    }

    @Test
    fun effectiveDocTypeNoOverrideReturnsNatural() {
        assertEquals(
            ReceiptDocumentType.INVOICE,
            ReceiptFormatter.effectiveDocumentType(ReceiptDocumentType.INVOICE, null),
        )
        assertEquals(
            ReceiptDocumentType.DEPOSIT_RECEIPT,
            ReceiptFormatter.effectiveDocumentType(ReceiptDocumentType.DEPOSIT_RECEIPT, null),
        )
    }

    // --- Tier-keyed attribution + watermark matrix ---

    private val logoBytes: ByteArray = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47) + ByteArray(8)

    @Test
    fun freeTierShowsFullAttribution() {
        val result = ReceiptFormatter.format(
            testOrder, testUser, garmentNames,
            tier = SubscriptionTier.FREE,
        )
        assertEquals(ReceiptAttribution.Full, result.attribution)
    }

    @Test
    fun proTierShowsCompactAttribution() {
        val result = ReceiptFormatter.format(
            testOrder, testUser, garmentNames,
            tier = SubscriptionTier.PRO,
        )
        assertEquals(ReceiptAttribution.Compact, result.attribution)
    }

    @Test
    fun atelierTierShowsNoAttribution() {
        val result = ReceiptFormatter.format(
            testOrder, testUser, garmentNames,
            tier = SubscriptionTier.ATELIER,
        )
        assertEquals(ReceiptAttribution.None, result.attribution)
    }

    @Test
    fun freeTierAlwaysUsesStitchPadWatermark() {
        // Free with logo: still StitchPad mark — paid feature can't be earned.
        val withLogo = ReceiptFormatter.format(
            testOrder, testUser, garmentNames,
            businessLogoBytes = logoBytes,
            tier = SubscriptionTier.FREE,
        )
        assertEquals(WatermarkSpec.StitchPadDiagonal, withLogo.watermark)

        // Free without logo: also StitchPad mark.
        val withoutLogo = ReceiptFormatter.format(
            testOrder, testUser, garmentNames,
            tier = SubscriptionTier.FREE,
        )
        assertEquals(WatermarkSpec.StitchPadDiagonal, withoutLogo.watermark)
    }

    @Test
    fun proTierUsesNoWatermarkRegardlessOfLogo() {
        // Paid tiers ship a clean document. The user-logo-as-watermark idea was
        // rolled back after design review — a photographic logo at low alpha
        // visually competes with content. Paid users still get their logo in
        // the header band; that's enough.
        val withLogo = ReceiptFormatter.format(
            testOrder, testUser, garmentNames,
            businessLogoBytes = logoBytes,
            tier = SubscriptionTier.PRO,
        )
        assertEquals(WatermarkSpec.None, withLogo.watermark)

        val withoutLogo = ReceiptFormatter.format(
            testOrder, testUser, garmentNames,
            tier = SubscriptionTier.PRO,
        )
        assertEquals(WatermarkSpec.None, withoutLogo.watermark)
    }

    @Test
    fun atelierTierUsesNoWatermarkRegardlessOfLogo() {
        val withLogo = ReceiptFormatter.format(
            testOrder, testUser, garmentNames,
            businessLogoBytes = logoBytes,
            tier = SubscriptionTier.ATELIER,
        )
        assertEquals(WatermarkSpec.None, withLogo.watermark)

        val withoutLogo = ReceiptFormatter.format(
            testOrder, testUser, garmentNames,
            tier = SubscriptionTier.ATELIER,
        )
        assertEquals(WatermarkSpec.None, withoutLogo.watermark)
    }
}
