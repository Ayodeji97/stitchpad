package com.danzucker.stitchpad.core.sharing

expect class OrderReceiptSharer {
    suspend fun shareReceiptAsImage(receiptData: ReceiptData)
    suspend fun shareReceiptAsPdf(receiptData: ReceiptData)
}
