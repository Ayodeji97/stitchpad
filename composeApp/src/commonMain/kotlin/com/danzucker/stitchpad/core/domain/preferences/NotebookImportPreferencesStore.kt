package com.danzucker.stitchpad.core.domain.preferences

import kotlinx.coroutines.flow.Flow

/** Device-local flags for the notebook scan import entry points. */
interface NotebookImportPreferencesStore {
    /** True once the one-time capture intro has been shown (or deliberately skipped). */
    suspend fun introShown(): Boolean
    suspend fun setIntroShown()

    /** True once the owner tapped "Not now" on the dashboard card. */
    fun observeCardDismissed(): Flow<Boolean>
    suspend fun dismissCard()
}
