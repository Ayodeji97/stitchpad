package com.danzucker.stitchpad.core.sharing

data class ReceiptItem(
    val quantity: Int,
    val garmentName: String,
    val formattedPrice: String
)

data class ReceiptData(
    val businessName: String,
    val businessPhone: String?,
    val customerName: String,
    val dateFormatted: String,
    val items: List<ReceiptItem>,
    val totalFormatted: String,
    val depositFormatted: String,
    val balanceFormatted: String,
    val isFullyPaid: Boolean,
    val statusLabel: String,
    val statusColorHex: String,
    val deadlineFormatted: String?,
    val priorityLabel: String?,
    val orderIdShort: String
)
