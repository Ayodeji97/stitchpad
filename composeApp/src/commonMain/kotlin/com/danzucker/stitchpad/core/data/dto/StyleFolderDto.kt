package com.danzucker.stitchpad.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class StyleFolderDto(
    val id: String = "",
    val name: String = "",
    val coverStyleId: String? = null,
    val styleCount: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
