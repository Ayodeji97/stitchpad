package com.danzucker.stitchpad.core.domain.model

data class Customer(
    val id: String,
    val userId: String,
    val name: String,
    val phone: String,
    val email: String? = null,
    val address: String? = null,
    val createdAt: Long = 0L,
    val slotState: CustomerSlotState = CustomerSlotState.ACTIVE,
    val lockedAt: Long? = null,
)
