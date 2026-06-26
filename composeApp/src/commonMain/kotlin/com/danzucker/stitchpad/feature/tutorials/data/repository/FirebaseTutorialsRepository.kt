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
                val remote = snapshot.documents.mapNotNull { doc ->
                    val dto = runCatching { doc.data<TutorialDto>() }.getOrNull() ?: return@mapNotNull null
                    if (!dto.enabled || dto.storagePath.isBlank()) return@mapNotNull null
                    dto.toTutorial(doc.id)
                }
                remote.takeIf { it.isNotEmpty() }?.sortedBy { it.sortOrder } ?: BUNDLED_TUTORIALS
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
