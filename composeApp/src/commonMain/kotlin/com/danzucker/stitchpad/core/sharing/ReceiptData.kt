package com.danzucker.stitchpad.core.sharing

data class ReceiptItem(
    val quantity: Int,
    val garmentName: String,
    val formattedPrice: String
)

/**
 * Pre-formatted payment record. Populated by [ReceiptFormatter] for any
 * order with at least one [Payment]; available to callers that want the
 * payment history (e.g. an in-app audit-trail view).
 *
 * The native receipt renderers intentionally do NOT draw a PAYMENTS
 * section on the shared document — design review decided the customer-
 * facing receipt should communicate payment state through the totals
 * block (Total / Deposit Paid / Balance) rather than a per-payment list.
 * Audit-trail use cases live on the in-app order detail screen instead.
 */
data class PaymentRow(
    val dateFormatted: String,
    val typeLabel: String,
    val methodLabel: String,
    val formattedAmount: String,
)

/**
 * Pre-formatted bank block displayed under the "PAY VIA TRANSFER" header.
 * Populated when the user has all three bank fields set AND the document
 * is not a fully-paid Receipt. Hidden otherwise.
 *
 * The native renderers do not yet draw this section — PR B2 will.
 */
data class BankBlock(
    val bankName: String,
    val accountName: String,
    val accountNumber: String,
)

/**
 * High-level document variant driven by `order.payments` + `balanceRemaining`:
 *  - INVOICE: no payments recorded yet (`payments.isEmpty()`).
 *  - DEPOSIT_RECEIPT: at least one payment, balance still > 0.
 *  - RECEIPT: fully paid (`balanceRemaining == 0`).
 *
 * The trio drives the doc-type chip in [ShareReceiptBottomSheet] and the
 * CTA visibility in [OrderDetailViewModel]. The label string for the
 * rendered document still lives in [ReceiptData.documentTypeLabel].
 */
enum class ReceiptDocumentType { INVOICE, DEPOSIT_RECEIPT, RECEIPT }

data class ReceiptData(
    val businessName: String,
    val businessPhone: String?,
    val documentType: ReceiptDocumentType,
    val documentTypeLabel: String,
    val customerName: String,
    val dateFormatted: String,
    val items: List<ReceiptItem>,
    val totalFormatted: String,
    val depositFormatted: String,
    val balanceFormatted: String,
    val isFullyPaid: Boolean,
    /** Pre-formatted payment rows. Empty when no payments recorded. Unused by renderers until PR B2. */
    val paymentRows: List<PaymentRow>,
    /**
     * Bank block to render under PAY VIA TRANSFER, or null if the user has no
     * bank details set or the document is a fully-paid Receipt. Unused by
     * renderers until PR B2.
     */
    val bankBlock: BankBlock?,
    val statusLabel: String,
    val statusColorHex: String,
    val deadlineFormatted: String?,
    val priorityLabel: String?,
    val orderIdShort: String,
    val attribution: String?,
    /**
     * Pre-decoded PNG bytes of the user's brand logo, or `null` if none set.
     * Pre-decoded because both renderers draw synchronously and can't await Coil.
     * Fetched via [coil3.ImageLoader.execute] then converted to PNG bytes at the
     * share-trigger call site (see [com.danzucker.stitchpad.feature.order.presentation.detail.OrderDetailViewModel]).
     */
    val businessLogoBytes: ByteArray?,
)
