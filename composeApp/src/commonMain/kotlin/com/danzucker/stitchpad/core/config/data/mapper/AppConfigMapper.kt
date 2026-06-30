package com.danzucker.stitchpad.core.config.data.mapper

import com.danzucker.stitchpad.core.config.data.dto.AppConfigDto
import com.danzucker.stitchpad.core.config.domain.model.AppConfig

fun AppConfigDto.toAppConfig(): AppConfig = AppConfig(
    communityEnabled = communityEnabled,
    communityInviteUrl = communityInviteUrl,
    billingEnabled = billingEnabled,
)
