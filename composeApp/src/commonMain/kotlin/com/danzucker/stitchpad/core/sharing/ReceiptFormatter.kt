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
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Suppress("TooManyFunctions")
object ReceiptFormatter {

    private const val FALLBACK_BUSINESS_NAME = "StitchPad"
    private const val ORDER_ID_PREFIX_LENGTH = 4

    /**
     * Build a [ReceiptData] from an order + user.
     *
     * [forceDocumentType] re-frames the label when the user picked Invoice vs
     * Deposit Receipt from the share-sheet chips. The override is intentionally
     * narrow: only meaningful on partial-paid orders (natural ==
     * `DEPOSIT_RECEIPT`) and only to `INVOICE` or `DEPOSIT_RECEIPT`. Any other
     * combination (forcing `RECEIPT` on an unpaid order, forcing anything on a
     * fully-paid one, forcing `DEPOSIT_RECEIPT` on a no-payments order) is
     * silently ignored. This guarantees the label can never contradict the
     * totals — a "RECEIPT" with ₦70,000 outstanding would mislead the customer.
     */
    fun format(
        order: Order,
        user: User,
        garmentNames: Map<GarmentType, String>,
        businessLogoBytes: ByteArray? = null,
        forceDocumentType: ReceiptDocumentType? = null,
        // Default to FREE so existing call sites and tests that don't yet care
        // about tier-keyed watermark/attribution keep working with the safest
        // fallback (StitchPad mark + full footer). Production share path in
        // OrderDetailViewModel passes the real tier from EntitlementsProvider.
        tier: SubscriptionTier = SubscriptionTier.FREE,
    ): ReceiptData {
        val tz = TimeZone.currentSystemDefault()
        val dateFormatted = formatDate(order.createdAt, tz)
        val deadlineFormatted = order.deadline?.let { formatDate(it, tz) }

        val groupedItems = groupReceiptItems(order.items, garmentNames)
        val shortId = order.id.take(ORDER_ID_PREFIX_LENGTH).uppercase()

        val naturalDocType = resolveDocumentType(order)
        val docType = applyDocTypeOverride(naturalDocType, forceDocumentType)

        // fullyPaid derives from the actual balance — NOT from docType. The
        // override re-frames the label only; the receipt's totals must never
        // lie about what's been paid, and bank-block visibility must track the
        // real outstanding amount (you can still owe ₦40k after picking
        // "Invoice" framing on a partial-paid order).
        val fullyPaid = order.balanceRemaining <= 0.0

        val paymentRows = order.payments
            .sortedBy { it.recordedAt }
            .map { it.toRow(tz) }

        // Bank block hidden when nothing is owed (no point asking for transfer).
        // Otherwise shown only when all three fields are set — group-required is
        // enforced in the form layer, so partial state should not reach here.
        val bankBlock = if (fullyPaid) null else user.toBankBlock()

        val attribution = attributionFor(tier)
        val watermark = watermarkFor(tier)

        return ReceiptData(
            businessName = user.businessName ?: FALLBACK_BUSINESS_NAME,
            // Prefer WhatsApp (V1 primary contact). Fall back to the legacy `phone`
            // slot for users onboarded before the WhatsApp field existed.
            businessPhone = user.whatsappNumber ?: user.phoneNumber,
            documentType = docType,
            documentTypeLabel = docType.label(),
            customerName = order.customerName,
            dateFormatted = dateFormatted,
            items = groupedItems,
            subtotalFormatted = "₦${formatPrice(order.totalPrice)}",
            discountFormatted = if (order.discount > 0.0) "−₦${formatPrice(order.discount)}" else null,
            discountReason = order.discountReason,
            totalFormatted = "₦${formatPrice(order.payableTotal)}",
            depositFormatted = "₦${formatPrice(order.depositPaid)}",
            balanceFormatted = "₦${formatPrice(order.balanceRemaining)}",
            isFullyPaid = fullyPaid,
            paymentRows = paymentRows,
            bankBlock = bankBlock,
            statusLabel = statusToLabel(order.status),
            statusColorHex = statusToColorHex(order.status),
            deadlineFormatted = deadlineFormatted,
            priorityLabel = when (order.priority) {
                OrderPriority.NORMAL -> null
                OrderPriority.URGENT -> "URGENT"
                OrderPriority.RUSH -> "RUSH"
            },
            orderIdShort = "ORD-$shortId",
            attribution = attribution,
            watermark = watermark,
            businessLogoBytes = businessLogoBytes,
        )
    }

    private fun attributionFor(tier: SubscriptionTier): ReceiptAttribution = when (tier) {
        SubscriptionTier.FREE -> ReceiptAttribution.Full
        SubscriptionTier.PRO -> ReceiptAttribution.Compact
        SubscriptionTier.ATELIER -> ReceiptAttribution.None
    }

    // Free always shows the StitchPad wordmark watermark (free distribution).
    // Paid tiers render the document clean — no StitchPad mark and no user-logo
    // watermark either. The earlier user-logo-as-watermark idea was rolled back
    // after design review: a photographic logo at low alpha visually competes
    // with the document content (see PR #96 review). The tailor's brand still
    // appears in the header band; that's enough.
    private fun watermarkFor(tier: SubscriptionTier): WatermarkSpec = when (tier) {
        SubscriptionTier.FREE -> WatermarkSpec.StitchPadDiagonal
        SubscriptionTier.PRO,
        SubscriptionTier.ATELIER -> WatermarkSpec.None
    }

    /**
     * Single source of truth for mapping an [Order] to its natural
     * [ReceiptDocumentType]. The share sheet reads this to decide whether to
     * show the Invoice / Deposit Receipt chips; the formatter reads it to
     * choose the label and downstream rendering. Any future change to the
     * classification (e.g. PR B2/B3 adding new states) must land here so
     * both call sites stay in lockstep.
     */
    fun resolveDocumentType(order: Order): ReceiptDocumentType = when {
        order.payments.isEmpty() -> ReceiptDocumentType.INVOICE
        order.balanceRemaining <= 0.0 -> ReceiptDocumentType.RECEIPT
        else -> ReceiptDocumentType.DEPOSIT_RECEIPT
    }

    /**
     * Public view of the doc type that [format] will actually produce for a
     * given natural type + user override. The share sheet uses this to title
     * itself ("Share Invoice" / "Share Receipt" / "Share Deposit Receipt") so
     * the title can never drift from the generated document (PTSP-29).
     */
    fun effectiveDocumentType(
        natural: ReceiptDocumentType,
        force: ReceiptDocumentType?,
    ): ReceiptDocumentType = applyDocTypeOverride(natural, force)

    /**
     * The override only flips the *framing* of a partial-paid order between
     * Invoice and Deposit Receipt. Every other transition would produce a
     * label that contradicts the totals (e.g. RECEIPT with positive balance,
     * or DEPOSIT_RECEIPT with no payments recorded).
     */
    private fun applyDocTypeOverride(
        natural: ReceiptDocumentType,
        force: ReceiptDocumentType?,
    ): ReceiptDocumentType = when {
        force == null -> natural
        natural != ReceiptDocumentType.DEPOSIT_RECEIPT -> natural
        force == ReceiptDocumentType.RECEIPT -> natural
        else -> force
    }

    private fun ReceiptDocumentType.label(): String = when (this) {
        ReceiptDocumentType.INVOICE -> "INVOICE"
        ReceiptDocumentType.DEPOSIT_RECEIPT -> "DEPOSIT RECEIPT"
        ReceiptDocumentType.RECEIPT -> "RECEIPT"
    }

    private fun User.toBankBlock(): BankBlock? {
        val name = bankName?.takeIf { it.isNotBlank() }
        val accountName = bankAccountName?.takeIf { it.isNotBlank() }
        val accountNumber = bankAccountNumber?.takeIf { it.isNotBlank() }
        if (name == null || accountName == null || accountNumber == null) return null
        return BankBlock(name, accountName, accountNumber)
    }

    private fun Payment.toRow(tz: TimeZone): PaymentRow = PaymentRow(
        dateFormatted = formatDate(recordedAt, tz),
        typeLabel = type.label(),
        methodLabel = method.label(),
        formattedAmount = "₦${formatPrice(amount)}",
    )

    private fun PaymentType.label(): String = when (this) {
        PaymentType.DEPOSIT -> "Deposit"
        PaymentType.PROGRESS -> "Progress"
        PaymentType.FINAL -> "Final"
    }

    private fun PaymentMethod.label(): String = when (this) {
        PaymentMethod.CASH -> "Cash"
        PaymentMethod.TRANSFER -> "Transfer"
        PaymentMethod.POS -> "POS"
        PaymentMethod.OTHER -> "Other"
    }

    private fun formatDate(epochMillis: Long, tz: TimeZone): String {
        val d = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(tz).date
        val month = d.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
        return "${d.dayOfMonth} $month ${d.year}"
    }

    /**
     * Group order items into receipt line items. Custom garments
     * (garmentType == OTHER with a non-blank customGarmentName) get their own
     * line item per distinct name, so the customer sees the tailor's actual
     * label (e.g. "Iro and Buba") instead of a generic "Other" bucket.
     *
     * Items also group by unit price: two items of the same garment merge only
     * when they share a price, so every line can show a truthful unit price
     * (PTSP-35). Same garment at differing prices stays on separate lines.
     */
    private fun groupReceiptItems(
        items: List<OrderItem>,
        garmentNames: Map<GarmentType, String>,
    ): List<ReceiptItem> = items
        .groupBy { item ->
            // Normalize to the case-insensitive contract the upsert path uses
            // (FirebaseCustomGarmentTypeRepository / GarmentPickerFilter). Two items
            // with the same name but differing casing must collapse into one line.
            val customName = item.customGarmentName
            val garmentKey = if (item.garmentType == GarmentType.OTHER && !customName.isNullOrBlank()) {
                "custom:${customName.trim().lowercase()}"
            } else {
                item.garmentType.name
            }
            // Pair garment with unit price so a merged line always shares one price.
            garmentKey to item.price
        }
        .map { (_, group) ->
            val first = group.first()
            val garmentName = if (first.garmentType == GarmentType.OTHER &&
                !first.customGarmentName.isNullOrBlank()
            ) {
                first.customGarmentName!!
            } else {
                garmentNames[first.garmentType] ?: first.garmentType.name
            }
            val totalQuantity = group.sumOf { it.quantity }
            ReceiptItem(
                quantity = totalQuantity,
                garmentName = garmentName,
                formattedUnitPrice = "₦${formatPrice(first.price)}",
                formattedPrice = "₦${formatPrice(first.price * totalQuantity)}",
            )
        }

    private fun statusToLabel(status: OrderStatus): String = when (status) {
        OrderStatus.PENDING -> "Pending"
        OrderStatus.IN_PROGRESS -> "In Progress"
        OrderStatus.READY -> "Ready"
        OrderStatus.DELIVERED -> "Delivered"
    }

    // Hex values mirror DesignTokens status colors used in StatusBadge so receipts
    // stay visually consistent with the in-app order cards.
    private fun statusToColorHex(status: OrderStatus): String = when (status) {
        OrderStatus.PENDING -> "#2B7FD4"
        OrderStatus.IN_PROGRESS -> "#E07B20"
        OrderStatus.READY -> "#2D9E6B"
        OrderStatus.DELIVERED -> "#7D7970"
    }
}
