package com.danzucker.stitchpad.core.domain.preferences

import kotlinx.coroutines.flow.Flow

interface ThemePreferencesStore {
    fun observeTheme(): Flow<ThemePreference>
    suspend fun getTheme(): ThemePreference
    suspend fun setTheme(theme: ThemePreference)
}
