package com.danzucker.stitchpad.core.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.danzucker.stitchpad.core.domain.preferences.NotebookImportPreferencesStore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

actual class NotebookImportPreferences(context: Context) : NotebookImportPreferencesStore {
    private val prefs = context.getSharedPreferences("notebook_import_prefs", Context.MODE_PRIVATE)

    override suspend fun introShown(): Boolean = prefs.getBoolean(KEY_INTRO_SHOWN, false)

    override suspend fun setIntroShown() {
        prefs.edit { putBoolean(KEY_INTRO_SHOWN, true) }
    }

    override fun observeCardDismissed(): Flow<Boolean> = callbackFlow {
        trySend(prefs.getBoolean(KEY_CARD_DISMISSED, false))
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_CARD_DISMISSED) trySend(prefs.getBoolean(KEY_CARD_DISMISSED, false))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    override suspend fun dismissCard() {
        prefs.edit { putBoolean(KEY_CARD_DISMISSED, true) }
    }

    companion object {
        private const val KEY_INTRO_SHOWN = "notebook_intro_shown"
        private const val KEY_CARD_DISMISSED = "notebook_card_dismissed"
    }
}
