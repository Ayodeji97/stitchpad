package com.danzucker.stitchpad.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class CustomerDto(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String? = null,
    val address: String? = null,
    val deliveryPreference: String = "PICKUP",
    val notes: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
