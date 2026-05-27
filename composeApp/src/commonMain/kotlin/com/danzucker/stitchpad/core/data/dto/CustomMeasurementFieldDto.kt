package com.danzucker.stitchpad.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class CustomMeasurementFieldDto(
    val id: String = "",
    val label: String = "",
    val genders: List<String> = emptyList(),
    val isArchived: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
