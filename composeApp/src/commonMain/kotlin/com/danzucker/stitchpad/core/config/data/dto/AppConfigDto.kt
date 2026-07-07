package com.danzucker.stitchpad.core.config.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class AppConfigDto(
    val communityEnabled: Boolean = false,
    val communityInviteUrl: String? = null,
    val billingEnabled: Boolean = false,
    val minSupportedBuildAndroid: Int? = null,
    val minSupportedBuildIos: Int? = null,
    val updateUrlAndroid: String? = null,
    val updateUrlIos: String? = null,
    val forceUpdateMessage: String? = null,
    val maintenanceMode: Boolean = false,
    val maintenanceMessage: String? = null,
)
