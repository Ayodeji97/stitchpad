package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.StyleDto
import com.danzucker.stitchpad.core.domain.model.ImageSyncState
import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.core.domain.model.StyleLocation
import kotlin.time.Clock

fun StyleDto.toStyle(location: StyleLocation): Style = Style(
    id = id,
    customerId = (location as? StyleLocation.CustomerCloset)?.customerId.orEmpty(),
    description = description,
    photoUrl = photoUrl,
    photoStoragePath = photoStoragePath,
    syncState = runCatching { ImageSyncState.valueOf(syncState) }.getOrDefault(ImageSyncState.SYNCED),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Style.toStyleDto(): StyleDto {
    val now = Clock.System.now().toEpochMilliseconds()
    return StyleDto(
        id = id,
        description = description,
        photoUrl = photoUrl,
        photoStoragePath = photoStoragePath,
        syncState = syncState.name,
        createdAt = if (createdAt == 0L) now else createdAt,
        updatedAt = now
    )
}
