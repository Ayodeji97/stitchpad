package com.danzucker.stitchpad.feature.tutorials.data.repository

import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.tutorials.data.BUNDLED_TUTORIALS
import com.danzucker.stitchpad.feature.tutorials.data.dto.TutorialDto
import com.danzucker.stitchpad.feature.tutorials.data.mapper.toTutorial
import com.danzucker.stitchpad.feature.tutorials.domain.model.Tutorial
import com.danzucker.stitchpad.feature.tutorials.domain.model.TutorialTopic
import com.danzucker.stitchpad.feature.tutorials.domain.repository.TutorialsRepository
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

private const val TAG = "TutorialsRepo"
private const val TUTORIALS_COLLECTION = "tutorials"

class FirebaseTutorialsRepository(
    private val firestore: FirebaseFirestore,
) : TutorialsRepository {

    override val tutorials: Flow<List<Tutorial>> =
        firestore.collection(TUTORIALS_COLLECTION)
            .snapshots()
            .map { snapshot ->
                val docs = snapshot.documents.mapNotNull { doc ->
                    runCatching { doc.id to doc.data<TutorialDto>() }.getOrNull()
                }
                mergeTutorialCatalog(docs)
            }
            .onStart { emit(BUNDLED_TUTORIALS) }
            .catch { throwable ->
                AppLogger.e(tag = TAG, throwable = throwable) { "observe tutorials failed" }
                emit(BUNDLED_TUTORIALS)
            }
            .distinctUntilChanged()

    override fun tutorial(id: String): Flow<Tutorial?> =
        tutorials.map { list -> list.firstOrNull { it.id == id } }.distinctUntilChanged()

    override fun forTopic(topic: TutorialTopic): Flow<Tutorial?> =
        tutorials.map { list -> list.firstOrNull { it.topicId == topic.id } }.distinctUntilChanged()
}

/**
 * Merges the remote `tutorials` docs with [BUNDLED_TUTORIALS]. Remote wins per topic: an enabled
 * remote doc replaces its bundled entry, an explicitly-disabled (or storage-less) remote doc keeps
 * that topic hidden, and a topic with no remote doc at all falls back to its bundled entry. This
 * keeps every contextual topic's hint card working during partial seeds while still honouring
 * remote enable/disable. Empty input → the full bundled catalog.
 */
internal fun mergeTutorialCatalog(docs: List<Pair<String, TutorialDto>>): List<Tutorial> {
    val presentTopicIds = docs.mapNotNull { (_, dto) -> dto.topicId.takeIf { it.isNotBlank() } }.toSet()
    val enabledRemote = docs.mapNotNull { (id, dto) ->
        if (!dto.enabled || dto.storagePath.isBlank()) null else dto.toTutorial(id)
    }
    val bundledFallback = BUNDLED_TUTORIALS.filter { it.topicId !in presentTopicIds }
    return (enabledRemote + bundledFallback).sortedBy { it.sortOrder }
}
