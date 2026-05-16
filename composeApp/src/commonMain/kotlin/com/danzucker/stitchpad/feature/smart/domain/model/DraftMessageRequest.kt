package com.danzucker.stitchpad.feature.smart.domain.model

import com.danzucker.stitchpad.core.smartinfra.domain.language.DraftLanguage

/**
 * Domain request passed from the ViewModel through the repository to the
 * Cloud Function.
 */
data class DraftMessageRequest(
    val customerId: String,
    val orderId: String,
    val intent: DraftIntent,
    val language: DraftLanguage,
    val customNotes: String? = null,
)
