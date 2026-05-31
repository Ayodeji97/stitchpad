package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.CustomGarmentTypeDto
import com.danzucker.stitchpad.core.domain.model.CustomGarmentType

fun CustomGarmentTypeDto.toCustomGarmentType(): CustomGarmentType =
    CustomGarmentType(
        id = id,
        name = name,
        createdAt = createdAt,
        lastUsedAt = lastUsedAt,
    )

fun CustomGarmentType.toDto(): CustomGarmentTypeDto =
    CustomGarmentTypeDto(
        id = id,
        name = name,
        createdAt = createdAt,
        lastUsedAt = lastUsedAt,
    )
