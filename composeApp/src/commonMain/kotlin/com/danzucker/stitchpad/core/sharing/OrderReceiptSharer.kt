package com.danzucker.stitchpad.core.sharing

import com.danzucker.stitchpad.core.domain.preferences.ReceiptImageStyle

expect class OrderReceiptSharer {
    suspend fun shareReceiptAsImage(receiptData: ReceiptData, style: ReceiptImageStyle)
    suspend fun shareReceiptAsPdf(receiptData: ReceiptData)
}
