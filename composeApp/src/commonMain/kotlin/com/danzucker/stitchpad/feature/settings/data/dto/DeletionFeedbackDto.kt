package com.danzucker.stitchpad.feature.settings.data.dto

import kotlinx.serialization.Serializable

/**
 * Anonymous account-deletion feedback. Intentionally contains NO user identifiers
 * (no userId, email, businessName, or phone) — Firestore security rules disallow
 * client reads on this collection so PMs aggregate it via the Firebase console.
 */
@Serializable
data class DeletionFeedbackDto(
    val reason: String = "",
    val additionalNotes: String? = null,
    val plan: String = "free",
    val daysActive: Int = 0,
    val platform: String = "android",
    val appVersion: String = "",
    val locale: String = "en",
    val createdAtEpochMs: Long = 0L,
)
