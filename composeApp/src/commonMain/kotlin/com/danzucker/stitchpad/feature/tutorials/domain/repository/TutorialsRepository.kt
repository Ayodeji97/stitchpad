package com.danzucker.stitchpad.feature.tutorials.domain.repository

import com.danzucker.stitchpad.feature.tutorials.domain.model.Tutorial
import com.danzucker.stitchpad.feature.tutorials.domain.model.TutorialTopic
import kotlinx.coroutines.flow.Flow

interface TutorialsRepository {

    /**
     * Hot stream of enabled tutorials sorted for the Help library, backed by a Firestore
     * snapshot listener. Emits a bundled fallback list before first load and on any read
     * error so the UI is never empty offline.
     */
    val tutorials: Flow<List<Tutorial>>

    /** Convenience: the single tutorial with [id], or null if not in the catalog. */
    fun tutorial(id: String): Flow<Tutorial?>

    /** Convenience: the tutorial bound to [topic], or null if none is published for it. */
    fun forTopic(topic: TutorialTopic): Flow<Tutorial?>
}
