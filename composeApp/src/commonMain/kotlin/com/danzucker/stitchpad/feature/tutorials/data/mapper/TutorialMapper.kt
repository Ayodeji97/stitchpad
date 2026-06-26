package com.danzucker.stitchpad.feature.tutorials.data.mapper

import com.danzucker.stitchpad.feature.tutorials.data.dto.TutorialDto
import com.danzucker.stitchpad.feature.tutorials.domain.model.Tutorial

/** Maps a Firestore [TutorialDto] (+ its document [id]) to the domain [Tutorial]. */
fun TutorialDto.toTutorial(id: String): Tutorial = Tutorial(
    id = id,
    topicId = topicId,
    title = title,
    description = description,
    storagePath = storagePath,
    thumbnailPath = thumbnailPath?.takeIf { it.isNotBlank() },
    durationSec = durationSec,
    sortOrder = sortOrder,
)
