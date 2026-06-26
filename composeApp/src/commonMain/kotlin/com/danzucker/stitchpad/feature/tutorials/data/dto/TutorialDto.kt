package com.danzucker.stitchpad.feature.tutorials.data.dto

import kotlinx.serialization.Serializable

/**
 * Firestore shape of a `tutorials/{docId}` document. A typed DTO is mandatory — reading
 * Firestore as `Map<String, Any?>` crashes on iOS Native (no serializer for Any?).
 * The document id is the [Tutorial][com.danzucker.stitchpad.feature.tutorials.domain.model.Tutorial]
 * id, so it is NOT duplicated as a field here.
 */
@Serializable
data class TutorialDto(
    val topicId: String = "",
    val title: String = "",
    val description: String = "",
    val storagePath: String = "",
    val thumbnailPath: String? = null,
    val durationSec: Int = 0,
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
)
