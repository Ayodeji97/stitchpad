package com.danzucker.stitchpad.feature.settings.domain

data class DeletionFeedback(
    val reason: DeletionReason,
    val additionalNotes: String?,
    val plan: String,
    val daysActive: Int,
    val platform: String,
    val appVersion: String,
    val locale: String,
)
