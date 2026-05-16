package com.danzucker.stitchpad.feature.smart.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape for the smartDraftMessage callable function response body.
 */
@Serializable
data class DraftMessageResponseDto(
    @SerialName("draftText") val draftText: String,
    @SerialName("remainingFreeQuota") val remainingFreeQuota: Int? = null,
)
