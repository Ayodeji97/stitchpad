package com.danzucker.stitchpad.feature.tutorials.data

import com.danzucker.stitchpad.feature.tutorials.domain.model.Tutorial
import com.danzucker.stitchpad.feature.tutorials.domain.model.TutorialTopic

/**
 * Offline / pre-load fallback catalog: one entry per contextual [TutorialTopic], pointing at
 * the canonical Firebase Storage paths. Titles/descriptions are intentionally blank — the
 * presentation layer resolves localized copy from [TutorialTopic] (see TutorialCopy), so this
 * data-layer constant holds no user-facing strings. The video still needs network (or a cached
 * file) to actually play; this only keeps the Help list and hint cards populated.
 */
internal val BUNDLED_TUTORIALS: List<Tutorial> = listOf(
    bundled(TutorialTopic.QuickStart, durationSec = 50, sortOrder = 0),
    bundled(TutorialTopic.AddCustomer, durationSec = 40, sortOrder = 1),
    bundled(TutorialTopic.CreateOrder, durationSec = 55, sortOrder = 2),
    bundled(TutorialTopic.Styles, durationSec = 45, sortOrder = 3),
    bundled(TutorialTopic.Reports, durationSec = 40, sortOrder = 4),
)

private fun bundled(topic: TutorialTopic, durationSec: Int, sortOrder: Int): Tutorial = Tutorial(
    id = topic.id,
    topicId = topic.id,
    title = "",
    description = "",
    storagePath = "tutorials/${topic.id}.mp4",
    thumbnailPath = "tutorials/${topic.id}_poster.jpg",
    durationSec = durationSec,
    sortOrder = sortOrder,
)
