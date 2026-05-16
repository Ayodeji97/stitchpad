package com.danzucker.stitchpad.feature.smart.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape for the smartDraftMessage callable function request body.
 * Field names + value strings match the server's TypeScript types.
 */
@Serializable
data class DraftMessageRequestDto(
    @SerialName("intentType") val intentType: String,
    @SerialName("customerId") val customerId: String,
    @SerialName("orderId") val orderId: String,
    @SerialName("language") val language: String,
    @SerialName("customNotes") val customNotes: String? = null,
)
