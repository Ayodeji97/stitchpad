package com.danzucker.stitchpad.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class CustomGarmentTypeDto(
    val id: String = "",
    val name: String = "",
    val createdAt: Long = 0L,
    val lastUsedAt: Long = 0L,
)
