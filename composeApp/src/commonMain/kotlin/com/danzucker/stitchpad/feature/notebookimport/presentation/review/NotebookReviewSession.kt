package com.danzucker.stitchpad.feature.notebookimport.presentation.review

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Same-person groups the tailor kept separate, per import job. In memory only (lost when the app
 * closes, so the prompt comes back), and shared so the review list and the card editor agree on which
 * groups still wait for Combine or Keep separate.
 */
class NotebookReviewSession {
    private val keptSeparate = MutableStateFlow<Map<String, Set<Set<String>>>>(emptyMap())

    fun dismissals(jobId: String): Flow<ReviewDismissals> =
        keptSeparate.map { ReviewDismissals(it[jobId].orEmpty()) }.distinctUntilChanged()

    fun keepSeparate(jobId: String, cardIds: Collection<String>) {
        keptSeparate.update { it + (jobId to (it[jobId].orEmpty() + setOf(cardIds.toSet()))) }
    }

    fun isKeptSeparate(jobId: String, cardIds: Collection<String>): Boolean =
        cardIds.toSet() in keptSeparate.value[jobId].orEmpty()
}
