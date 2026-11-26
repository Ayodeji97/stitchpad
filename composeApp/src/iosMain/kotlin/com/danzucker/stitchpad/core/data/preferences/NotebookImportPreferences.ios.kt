package com.danzucker.stitchpad.core.data.preferences

import com.danzucker.stitchpad.core.domain.preferences.NotebookImportPreferencesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSUserDefaults

actual class NotebookImportPreferences : NotebookImportPreferencesStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    private val cardDismissed = MutableStateFlow(defaults.boolForKey(KEY_CARD_DISMISSED))

    override suspend fun introShown(): Boolean = defaults.boolForKey(KEY_INTRO_SHOWN)

    override suspend fun setIntroShown() {
        defaults.setBool(true, forKey = KEY_INTRO_SHOWN)
    }

    override fun observeCardDismissed(): Flow<Boolean> = cardDismissed.asStateFlow()

    override suspend fun dismissCard() {
        defaults.setBool(true, forKey = KEY_CARD_DISMISSED)
        cardDismissed.value = true
    }

    companion object {
        private const val KEY_INTRO_SHOWN = "notebook_intro_shown"
        private const val KEY_CARD_DISMISSED = "notebook_card_dismissed"
    }
}
