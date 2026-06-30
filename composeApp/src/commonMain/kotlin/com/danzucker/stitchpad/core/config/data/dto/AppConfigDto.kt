package com.danzucker.stitchpad.core.config.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class AppConfigDto(
    val communityEnabled: Boolean = false,
    val communityInviteUrl: String? = null,
    val billingEnabled: Boolean = false,
)
