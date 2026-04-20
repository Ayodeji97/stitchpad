package com.danzucker.stitchpad.core.sharing

import com.danzucker.stitchpad.core.domain.model.Order

expect class OrderReceiptSharer {
    suspend fun shareReceipt(order: Order)
}
