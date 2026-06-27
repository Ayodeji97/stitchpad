package com.danzucker.stitchpad.feature.tutorials.domain

import com.danzucker.stitchpad.feature.tutorials.domain.model.Tutorial

/**
 * Resolves a [Tutorial] to a playable URI (cached local file or a streaming URL). Interface so
 * the player ViewModel can be unit-tested without the Firebase-backed implementation.
 */
fun interface TutorialUriResolver {
    suspend fun resolvePlayableUri(tutorial: Tutorial): String?
}
