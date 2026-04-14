package com.danzucker.stitchpad.core.domain.model

data class Measurement(
    val id: String,
    val customerId: String,
    val gender: CustomerGender,
    val fields: Map<String, Double>,
    val unit: MeasurementUnit,
    val notes: String?,
    val dateTaken: Long,
    val createdAt: Long
)
