package com.danzucker.stitchpad.feature.tutorials

import com.danzucker.stitchpad.feature.tutorials.domain.model.Tutorial
import com.danzucker.stitchpad.feature.tutorials.domain.model.TutorialTopic
import com.danzucker.stitchpad.feature.tutorials.domain.repository.TutorialsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeTutorialsRepository(
    initial: List<Tutorial> = emptyList(),
) : TutorialsRepository {

    val tutorialsFlow = MutableStateFlow(initial)

    override val tutorials: Flow<List<Tutorial>> = tutorialsFlow

    override fun tutorial(id: String): Flow<Tutorial?> =
        tutorialsFlow.map { list -> list.firstOrNull { it.id == id } }

    override fun forTopic(topic: TutorialTopic): Flow<Tutorial?> =
        tutorialsFlow.map { list -> list.firstOrNull { it.topicId == topic.id } }
}

fun tutorial(
    id: String,
    topicId: String = id,
    storagePath: String = "tutorials/$id.mp4",
    durationSec: Int = 40,
    sortOrder: Int = 0,
): Tutorial = Tutorial(
    id = id,
    topicId = topicId,
    title = "",
    description = "",
    storagePath = storagePath,
    thumbnailPath = null,
    durationSec = durationSec,
    sortOrder = sortOrder,
)
