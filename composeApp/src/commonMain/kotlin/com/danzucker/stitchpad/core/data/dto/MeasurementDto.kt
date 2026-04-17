package com.danzucker.stitchpad.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class MeasurementDto(
    val id: String = "",
    val gender: String = "FEMALE",
    val bodyShape: String? = null,
    val fields: Map<String, Double> = emptyMap(),
    val unit: String = "INCHES",
    val notes: String? = null,
    val dateTaken: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    // Legacy field — kept for backwards compatibility with Sprint 2 records
    val garmentType: String? = null
)
