package com.danzucker.stitchpad.feature.smart.domain.model

/**
 * Compact view of a Customer used by the Draft Message picker. Includes
 * just the fields the picker needs to render + decide WhatsApp send
 * eligibility.
 */
data class CustomerSummary(
    val id: String,
    val firstName: String,
    val whatsappNumber: String?,
)
