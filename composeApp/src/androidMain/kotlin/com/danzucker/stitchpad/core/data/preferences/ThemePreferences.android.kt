package com.danzucker.stitchpad.core.data.preferences

import android.content.Context
import androidx.core.content.edit
import com.danzucker.stitchpad.core.domain.preferences.ThemePreference
import com.danzucker.stitchpad.core.domain.preferences.ThemePreferencesStore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

actual class ThemePreferences(context: Context) : ThemePreferencesStore {
    private val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    override fun observeTheme(): Flow<ThemePreference> = callbackFlow {
        trySend(readTheme())
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_THEME) trySend(readTheme())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    override suspend fun getTheme(): ThemePreference = readTheme()

    override suspend fun setTheme(theme: ThemePreference) {
        prefs.edit { putString(KEY_THEME, theme.name) }
    }

    private fun readTheme(): ThemePreference =
        runCatching { ThemePreference.valueOf(prefs.getString(KEY_THEME, null) ?: ThemePreference.SYSTEM.name) }
            .getOrDefault(ThemePreference.SYSTEM)

    companion object {
        private const val KEY_THEME = "theme_preference"
    }
}
