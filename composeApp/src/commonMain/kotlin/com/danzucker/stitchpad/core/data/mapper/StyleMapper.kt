package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.StyleDto
import com.danzucker.stitchpad.core.domain.model.Style
import kotlin.time.Clock

fun StyleDto.toStyle(customerId: String): Style = Style(
    id = id,
    customerId = customerId,
    description = description,
    photoUrl = photoUrl,
    photoStoragePath = photoStoragePath,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Style.toStyleDto(): StyleDto {
    val now = Clock.System.now().toEpochMilliseconds()
    return StyleDto(
        id = id,
        description = description,
        photoUrl = photoUrl,
        photoStoragePath = photoStoragePath,
        createdAt = if (createdAt == 0L) now else createdAt,
        updatedAt = now
    )
}
