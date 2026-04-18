package com.danzucker.stitchpad.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class StyleDto(
    val id: String = "",
    val description: String = "",
    val photoUrl: String = "",
    val photoStoragePath: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
