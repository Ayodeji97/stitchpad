package com.danzucker.stitchpad.core.sharing

import com.danzucker.stitchpad.core.domain.model.Order

expect class OrderReceiptSharer {
    fun shareReceipt(order: Order)
}
