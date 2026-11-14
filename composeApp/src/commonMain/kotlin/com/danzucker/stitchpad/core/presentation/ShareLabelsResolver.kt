package com.danzucker.stitchpad.core.presentation

import com.danzucker.stitchpad.core.sharing.ReceiptLabels
import org.jetbrains.compose.resources.getString
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.order_status_delivered
import stitchpad.composeapp.generated.resources.order_status_in_progress
import stitchpad.composeapp.generated.resources.order_status_pending
import stitchpad.composeapp.generated.resources.order_status_ready
import stitchpad.composeapp.generated.resources.payment_method_cash
import stitchpad.composeapp.generated.resources.payment_method_other
import stitchpad.composeapp.generated.resources.payment_method_pos
import stitchpad.composeapp.generated.resources.payment_method_transfer
import stitchpad.composeapp.generated.resources.receipt_doc_deposit_receipt
import stitchpad.composeapp.generated.resources.receipt_doc_invoice
import stitchpad.composeapp.generated.resources.receipt_doc_receipt
import stitchpad.composeapp.generated.resources.receipt_footer_compact
import stitchpad.composeapp.generated.resources.receipt_footer_full
import stitchpad.composeapp.generated.resources.receipt_label_account_name
import stitchpad.composeapp.generated.resources.receipt_label_account_number
import stitchpad.composeapp.generated.resources.receipt_label_amount_due
import stitchpad.composeapp.generated.resources.receipt_label_balance
import stitchpad.composeapp.generated.resources.receipt_label_bank
import stitchpad.composeapp.generated.resources.receipt_label_customer
import stitchpad.composeapp.generated.resources.receipt_label_date
import stitchpad.composeapp.generated.resources.receipt_label_deadline
import stitchpad.composeapp.generated.resources.receipt_label_deposit_paid
import stitchpad.composeapp.generated.resources.receipt_label_discount
import stitchpad.composeapp.generated.resources.receipt_label_items
import stitchpad.composeapp.generated.resources.receipt_label_order_number
import stitchpad.composeapp.generated.resources.receipt_label_paid_in_full
import stitchpad.composeapp.generated.resources.receipt_label_pay_via_transfer
import stitchpad.composeapp.generated.resources.receipt_label_status
import stitchpad.composeapp.generated.resources.receipt_label_subtotal
import stitchpad.composeapp.generated.resources.receipt_label_total
import stitchpad.composeapp.generated.resources.receipt_label_unit_price_each
import stitchpad.composeapp.generated.resources.receipt_payment_deposit
import stitchpad.composeapp.generated.resources.receipt_payment_final
import stitchpad.composeapp.generated.resources.receipt_payment_progress
import stitchpad.composeapp.generated.resources.receipt_priority_rush
import stitchpad.composeapp.generated.resources.receipt_priority_urgent

/**
 * Resolves every fixed receipt string from Compose resources in the device /
 * app language. Called at the share call site (ViewModel) so
 * [com.danzucker.stitchpad.core.sharing.ReceiptFormatter] and the native
 * renderers stay resource-free. Template fields are fetched raw (no args) and
 * filled per row by [ReceiptLabels].
 */
@Suppress("LongMethod")
suspend fun resolveReceiptLabels(): ReceiptLabels = ReceiptLabels(
    documentInvoice = getString(Res.string.receipt_doc_invoice),
    documentDepositReceipt = getString(Res.string.receipt_doc_deposit_receipt),
    documentReceipt = getString(Res.string.receipt_doc_receipt),
    priorityUrgent = getString(Res.string.receipt_priority_urgent),
    priorityRush = getString(Res.string.receipt_priority_rush),
    paymentDeposit = getString(Res.string.receipt_payment_deposit),
    paymentProgress = getString(Res.string.receipt_payment_progress),
    paymentFinal = getString(Res.string.receipt_payment_final),
    methodCash = getString(Res.string.payment_method_cash),
    methodTransfer = getString(Res.string.payment_method_transfer),
    methodPos = getString(Res.string.payment_method_pos),
    methodOther = getString(Res.string.payment_method_other),
    statusPending = getString(Res.string.order_status_pending),
    statusInProgress = getString(Res.string.order_status_in_progress),
    statusReady = getString(Res.string.order_status_ready),
    statusDelivered = getString(Res.string.order_status_delivered),
    customer = getString(Res.string.receipt_label_customer),
    date = getString(Res.string.receipt_label_date),
    items = getString(Res.string.receipt_label_items),
    subtotal = getString(Res.string.receipt_label_subtotal),
    discount = getString(Res.string.receipt_label_discount),
    depositPaid = getString(Res.string.receipt_label_deposit_paid),
    total = getString(Res.string.receipt_label_total),
    balance = getString(Res.string.receipt_label_balance),
    payViaTransfer = getString(Res.string.receipt_label_pay_via_transfer),
    bank = getString(Res.string.receipt_label_bank),
    accountName = getString(Res.string.receipt_label_account_name),
    accountNumber = getString(Res.string.receipt_label_account_number),
    status = getString(Res.string.receipt_label_status),
    deadline = getString(Res.string.receipt_label_deadline),
    paidInFull = getString(Res.string.receipt_label_paid_in_full),
    amountDueTemplate = getString(Res.string.receipt_label_amount_due),
    unitPriceEachTemplate = getString(Res.string.receipt_label_unit_price_each),
    orderNumberTemplate = getString(Res.string.receipt_label_order_number),
    footerFull = getString(Res.string.receipt_footer_full),
    footerCompact = getString(Res.string.receipt_footer_compact),
)
