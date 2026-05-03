package com.danzucker.stitchpad.core.domain.model

/**
 * Single payment recorded against an [Order]. The list of payments on an
 * order is the source of truth for what's been paid; [Order.depositPaid]
 * derives from sum of payments.
 */
data class Payment(
    val id: String,
    val amount: Double,
    val method: PaymentMethod,
    val type: PaymentType,
    val recordedAt: Long,
    val note: String? = null,
)

enum class PaymentMethod {
    CASH,
    TRANSFER,
    POS,
    OTHER,
}

enum class PaymentType {
    DEPOSIT,
    PROGRESS,
    FINAL,
}
