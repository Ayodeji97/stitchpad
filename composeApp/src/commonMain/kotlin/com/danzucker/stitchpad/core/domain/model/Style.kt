package com.danzucker.stitchpad.core.domain.model

data class Style(
    val id: String,
    val customerId: String,
    val description: String,
    val photoUrl: String,
    val photoStoragePath: String,
    val createdAt: Long,
    val updatedAt: Long,
    val syncState: ImageSyncState = ImageSyncState.SYNCED,
    val localPhotoPath: String? = null,
    /**
     * True when this style's image is shared with at least one other style
     * (created by copy/move across customers). Deleting a shared style removes
     * only its Firestore doc — never the storage object — so a copy elsewhere
     * keeps rendering. See StyleRepository.copyStyle/moveStyle.
     */
    val sharesImage: Boolean = false,
)
