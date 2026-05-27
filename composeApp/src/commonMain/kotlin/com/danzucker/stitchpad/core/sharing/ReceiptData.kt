package com.danzucker.stitchpad.core.sharing

data class ReceiptItem(
    val quantity: Int,
    val garmentName: String,
    val formattedPrice: String
)

data class ReceiptData(
    val businessName: String,
    val businessPhone: String?,
    val documentTypeLabel: String,
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
