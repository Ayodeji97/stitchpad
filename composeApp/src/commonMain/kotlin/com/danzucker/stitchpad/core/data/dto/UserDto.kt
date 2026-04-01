package com.danzucker.stitchpad.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val businessName: String? = null,
    val phoneNumber: String? = null,
    val avatarColorIndex: Int = 0
)
