package com.danzucker.stitchpad.core.domain.preferences

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeNotebookImportPreferencesStore(
    introShown: Boolean = false,
    cardDismissed: Boolean = false,
) : NotebookImportPreferencesStore {
    private val intro = MutableStateFlow(introShown)
    private val dismissed = MutableStateFlow(cardDismissed)

    override suspend fun introShown(): Boolean = intro.value
    override suspend fun setIntroShown() {
        intro.value = true
    }

    override fun observeCardDismissed(): Flow<Boolean> = dismissed
    override suspend fun dismissCard() {
        dismissed.value = true
    }
}
