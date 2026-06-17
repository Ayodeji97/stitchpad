package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.StyleFolderDto
import com.danzucker.stitchpad.core.domain.model.StyleFolder
import kotlin.time.Clock

fun StyleFolderDto.toStyleFolder(): StyleFolder =
    StyleFolder(id, name, coverStyleId, styleCount, createdAt, updatedAt)

fun StyleFolder.toDto(): StyleFolderDto {
    val now = Clock.System.now().toEpochMilliseconds()
    return StyleFolderDto(
        id = id,
        name = name,
        coverStyleId = coverStyleId,
        styleCount = styleCount,
        createdAt = if (createdAt == 0L) now else createdAt,
        updatedAt = now,
    )
}
