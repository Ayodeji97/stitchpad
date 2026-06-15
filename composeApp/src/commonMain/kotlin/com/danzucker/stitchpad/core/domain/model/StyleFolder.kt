package com.danzucker.stitchpad.core.domain.model

data class StyleFolder(
    val id: String,
    val name: String,
    val coverStyleId: String? = null,
    val styleCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)
