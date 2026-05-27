package com.danzucker.stitchpad.core.domain.model

data class CustomMeasurementField(
    val id: String,
    val label: String,
    val genders: Set<CustomerGender>,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
