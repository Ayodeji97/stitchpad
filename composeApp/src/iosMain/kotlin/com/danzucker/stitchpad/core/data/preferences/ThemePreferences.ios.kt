package com.danzucker.stitchpad.core.data.preferences

import com.danzucker.stitchpad.core.domain.preferences.ThemePreference
import com.danzucker.stitchpad.core.domain.preferences.ThemePreferencesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSUserDefaults

actual class ThemePreferences : ThemePreferencesStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    private val flow = MutableStateFlow(readTheme())

    override fun observeTheme(): Flow<ThemePreference> = flow.asStateFlow()

    override suspend fun getTheme(): ThemePreference = readTheme()

    override suspend fun setTheme(theme: ThemePreference) {
        defaults.setObject(theme.name, forKey = KEY_THEME)
        flow.value = theme
    }

    private fun readTheme(): ThemePreference =
        runCatching { ThemePreference.valueOf(defaults.stringForKey(KEY_THEME) ?: ThemePreference.SYSTEM.name) }
            .getOrDefault(ThemePreference.SYSTEM)

    companion object {
        private const val KEY_THEME = "theme_preference"
    }
}
