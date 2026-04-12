package com.danzucker.stitchpad.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class MeasurementDto(
    val id: String = "",
    val garmentType: String = "",
    val fields: Map<String, Double> = emptyMap(),
    val unit: String = "INCHES",
    val notes: String? = null,
    val dateTaken: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
