package com.danzucker.stitchpad.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class CustomerDto(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String? = null,
    val address: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    /** "active" | "locked" — see CustomerSlotState. Missing on legacy docs → ACTIVE. */
    val slotState: String = "active",
    /** Epoch millis when slotState was set to "locked", null otherwise. */
    val lockedAt: Long? = null,
)
