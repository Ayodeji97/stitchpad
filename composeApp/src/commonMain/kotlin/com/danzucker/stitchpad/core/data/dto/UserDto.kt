package com.danzucker.stitchpad.core.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val businessName: String? = null,
    @SerialName("phone")
    val phoneNumber: String? = null,
    @SerialName("whatsapp")
    val whatsappNumber: String? = null,
    val avatarColorIndex: Int = 0
)
