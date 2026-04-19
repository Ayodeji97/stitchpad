package com.danzucker.stitchpad.core.domain.model

data class Style(
    val id: String,
    val customerId: String,
    val description: String,
    val photoUrl: String,
    val photoStoragePath: String,
    val createdAt: Long,
    val updatedAt: Long
)
